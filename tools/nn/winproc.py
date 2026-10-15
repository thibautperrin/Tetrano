"""Process helpers (Windows-aware, no torch import): children that die with their parent,
below-normal priority, process identity for PID files.

On Windows, `run_child` puts each child process in a job object created with
JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE: when this process ends for any reason (Ctrl+C, closed console,
taskkill /F of this process alone, crash), the system closes the job handle and kills the child
and its own descendants. Elsewhere it is a plain subprocess.
"""
import subprocess
import sys

_job = None


def _kill_on_close_job():
    global _job
    if _job is None:
        import ctypes
        from ctypes import wintypes

        class BasicLimits(ctypes.Structure):
            _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64), ("PerJobUserTimeLimit", ctypes.c_int64),
                        ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                        ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                        ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD),
                        ("SchedulingClass", wintypes.DWORD)]

        class IoCounters(ctypes.Structure):
            _fields_ = [(n, ctypes.c_ulonglong) for n in ("ReadOperationCount", "WriteOperationCount",
                                                           "OtherOperationCount", "ReadTransferCount",
                                                           "WriteTransferCount", "OtherTransferCount")]

        class ExtendedLimits(ctypes.Structure):
            _fields_ = [("BasicLimitInformation", BasicLimits), ("IoInfo", IoCounters),
                        ("ProcessMemoryLimit", ctypes.c_size_t), ("JobMemoryLimit", ctypes.c_size_t),
                        ("PeakProcessMemoryUsed", ctypes.c_size_t), ("PeakJobMemoryUsed", ctypes.c_size_t)]

        k32 = ctypes.WinDLL("kernel32", use_last_error=True)
        k32.CreateJobObjectW.restype = wintypes.HANDLE
        k32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        k32.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
        k32.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
        job = k32.CreateJobObjectW(None, None)
        if not job:
            raise OSError(ctypes.get_last_error(), "CreateJobObject failed")
        info = ExtendedLimits()
        info.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not k32.SetInformationJobObject(job, 9, ctypes.byref(info), ctypes.sizeof(info)):  # extended limits
            raise OSError(ctypes.get_last_error(), "SetInformationJobObject failed")
        _job = (k32, job)  # the handle stays open until this process exits
    return _job


def kill_with_parent(proc):
    """Ties a Popen child (and its future descendants) to this process's lifetime (Windows only)."""
    if sys.platform != "win32":
        return
    try:
        k32, job = _kill_on_close_job()
        if not k32.AssignProcessToJobObject(job, int(proc._handle)):
            import ctypes
            print(f"warning: AssignProcessToJobObject failed ({ctypes.get_last_error()})", flush=True)
    except OSError as e:
        print(f"warning: no job object ({e}); the child may outlive this process", flush=True)


def run_child(cmd, timeout=None, capture=True):
    """Like subprocess.run(cmd, capture_output=capture, text=True, timeout=timeout), but the child
    is killed with this process. Returns a CompletedProcess; raises subprocess.TimeoutExpired."""
    pipe = subprocess.PIPE if capture else None
    with subprocess.Popen(cmd, stdout=pipe, stderr=pipe, text=capture) as proc:
        kill_with_parent(proc)
        try:
            out, err = proc.communicate(timeout=timeout)
        except BaseException:  # timeout, Ctrl+C...: the child must not survive
            proc.kill()
            proc.wait()
            raise
    return subprocess.CompletedProcess(cmd, proc.returncode, out, err)


def keep_system_awake():
    """Windows: ask the OS not to idle-sleep while this process runs (released when it exits)."""
    if sys.platform == "win32":
        import ctypes
        es_continuous, es_system_required = 0x80000000, 0x00000001
        ctypes.windll.kernel32.SetThreadExecutionState(es_continuous | es_system_required)


def below_normal_priority():
    """Lowers this process's priority class; children inherit it (Windows only)."""
    if sys.platform == "win32":
        import ctypes
        from ctypes import wintypes
        k32 = ctypes.WinDLL("kernel32", use_last_error=True)
        k32.GetCurrentProcess.restype = wintypes.HANDLE
        k32.SetPriorityClass.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        k32.SetPriorityClass(k32.GetCurrentProcess(), 0x4000)  # BELOW_NORMAL_PRIORITY_CLASS


def creation_time(pid):
    """An identifier of the process `pid` (its creation time), or None if it is not running.
    Used to make sure a PID read from a file still designates the same process."""
    if sys.platform != "win32":
        import os
        try:
            os.kill(pid, 0)
        except OSError:
            return None
        return 0
    import ctypes
    from ctypes import wintypes
    k32 = ctypes.WinDLL("kernel32", use_last_error=True)
    k32.OpenProcess.restype = wintypes.HANDLE
    k32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    k32.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.c_void_p]
    k32.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.c_void_p] * 4
    k32.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = k32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not handle:
        return None
    try:
        code = wintypes.DWORD()
        if not k32.GetExitCodeProcess(handle, ctypes.byref(code)) or code.value != 259:  # STILL_ACTIVE
            return None
        times = [ctypes.c_ulonglong() for _ in range(4)]
        if not k32.GetProcessTimes(handle, *[ctypes.byref(t) for t in times]):
            return None
        return times[0].value
    finally:
        k32.CloseHandle(handle)

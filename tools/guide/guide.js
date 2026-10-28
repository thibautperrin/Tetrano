document.querySelectorAll('.stepper').forEach(function (stepper) {
  var frames = stepper.querySelectorAll('.frame');
  var counter = stepper.querySelector('.counter');
  var current = frames.length - 1;
  function show(i) {
    current = Math.max(0, Math.min(frames.length - 1, i));
    frames.forEach(function (f, k) { f.hidden = k !== current; });
    counter.textContent = (current + 1) + ' / ' + frames.length;
  }
  stepper.querySelector('.first').addEventListener('click', function () { show(0); });
  stepper.querySelector('.prev').addEventListener('click', function () { show(current - 1); });
  stepper.querySelector('.next').addEventListener('click', function () { show(current + 1); });
  stepper.querySelector('.last').addEventListener('click', function () { show(frames.length - 1); });
  stepper.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowLeft') { show(current - 1); e.preventDefault(); }
    if (e.key === 'ArrowRight') { show(current + 1); e.preventDefault(); }
  });
  show(0);
});

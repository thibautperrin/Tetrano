# Strings and where they are used (generated from the Kotlin sources)

Each entry: key, English source, and the code that uses it (file:line and surrounding code).

## `Play` (string)
- English: Play
- used in `ChallengeScreen.kt:330`: `text = { DialogText(stringResource(R.string.Explanations_Challenge2)) }, confirmButton = { TextButton(onClick = { onAction(ChallengeAction.ExplanationsDismissed) }) { Text(stringResource(R.string.Play)) } },`
- used in `HomeScreen.kt:100`: `.fillMaxWidth() LegacyButton( text = stringResource(R.string.Play), onClick = {`
- used in `ComputerSetupScreen.kt:74`: `val play = @Composable { modifier: Modifier -> LegacyButton( text = stringResource(R.string.Play), onClick = { onAction(ComputerSetupAction.PlayClicked) },`

## `About` (string)
- English: About
- used in `AboutScreen.kt:55`: `containerColor = TetranoPalette.Page, contentWindowInsets = WindowInsets.safeDrawing, topBar = { LegacyTopBar(title = stringResource(R.string.About)) }, ) { padding ->`
- used in `HomeScreen.kt:122`: `) LegacyButton( text = stringResource(R.string.About), onClick = { onAction(HomeAction.AboutClicked) },`

## `action_settings` (string)
- English: Settings
- used in `ChallengeScreen.kt:148`: `DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) { DropdownMenuItem( text = { Text(stringResource(R.string.action_settings)) }, onClick = {`
- used in `GameScreen.kt:177`: `item(R.string.Play_Again) { onAction(GameAction.PlayAgainClicked) } item(R.string.Mot_Clignoter) { onAction(GameAction.BlinkClicked) } if (mode == GameMode.VS_COMPUTER) item(R.string.action_settings, onNavigateToSettings`
- used in `HomeScreen.kt:116`: `) LegacyButton( text = stringResource(R.string.action_settings), onClick = { onAction(HomeAction.SettingsClicked) },`

## `Rules_Of_Game` (string)
- English: Rules of game
- used in `HomeScreen.kt:110`: `) LegacyButton( text = stringResource(R.string.Rules_Of_Game), onClick = { onAction(HomeAction.RulesClicked) },`
- used in `RulesScreen.kt:57`: `containerColor = TetranoPalette.Page, contentWindowInsets = WindowInsets.safeDrawing, topBar = { LegacyTopBar(title = stringResource(R.string.Rules_Of_Game)) }, ) { padding ->`

## `Long_Sentence_Rules` (string)
- English: Tetrano is a game for two players.\n\nTake turns placing a ring of your color on the board. The first player to line up four rings of their color wins.\n\nA line of four rings can be:\n• concentric: four rings of different sizes in the same square;\n• straight: four rings on a row, a column or a diagonal, either all the same size, or in increasing or decreasing size.\n\nTo play, drag a ring from below the board and drop it on a square.\n\nHave fun!
- used in `RulesScreen.kt:68`: `Column(modifier = Modifier.fillMaxSize()) { Text( text = stringResource(R.string.Long_Sentence_Rules), color = TetranoPalette.Text,`

## `Winning_Situation_Example` (string)
- English: Winning situation examples
- used in `RulesScreen.kt:77`: `) LegacyButton( text = stringResource(R.string.Winning_Situation_Example), onClick = { onAction(RulesAction.WinningExamplesClicked) },`
- used in `WinningExamplesScreen.kt:52`: `containerColor = TetranoPalette.Page, contentWindowInsets = WindowInsets.safeDrawing, topBar = { LegacyTopBar(title = stringResource(R.string.Winning_Situation_Example)) }, ) { padding ->`

## `User` (string)
- English: User
- used in `GameScreen.kt:187`: `// ApplicationTetrano's preference defaults: Joueur1/Joueur2, and User against the computer. val player1Name = uiModel.player1Name.ifBlank { stringResource(if (mode == GameMode.VS_COMPUTER) R.string.User else R.string.Jo`
- used in `SettingsScreen.kt:163`: `onValueChange = { onAction(SettingsAction.UserNameChanged(it)) }, label = { Text(stringResource(R.string.User_Name)) }, placeholder = { Text(uiModel.currentUserName.ifBlank { stringResource(R.string.User) }) }, singleLin`
- used in `ComputerSetupScreen.kt:121`: `Column(Modifier.selectableGroup()) { WhoBeginsRadio( text = stringResource(R.string.User), selected = uiModel.userBegins,`

## `User_Name` (string)
- English: User name
- used in `SettingsScreen.kt:162`: `value = uiModel.userNameDraft, onValueChange = { onAction(SettingsAction.UserNameChanged(it)) }, label = { Text(stringResource(R.string.User_Name)) }, placeholder = { Text(uiModel.currentUserName.ifBlank { stringResource`

## `Two_Players` (string)
- English: 2 players
- used in `PlayMenuScreen.kt:82`: `val columnGap = if (large && landscape) dimens.smallMargin * 2 else 0.dp  val twoPlayers = PlayButton(R.drawable.bouton_2_joueurs, R.string.Two_Players, PlayMenuAction.TwoPlayersClicked) val onePlayer = PlayButton(R.draw`
- used in `TwoPlayersSetupScreen.kt:118`: `}  SetupPage(title = stringResource(R.string.Two_Players)) { val landscape = isLandscape()`

## `One_Player` (string)
- English: 1 player
- used in `PlayMenuScreen.kt:83`: ` val twoPlayers = PlayButton(R.drawable.bouton_2_joueurs, R.string.Two_Players, PlayMenuAction.TwoPlayersClicked) val onePlayer = PlayButton(R.drawable.bouton_1_joueur, R.string.One_Player, PlayMenuAction.OnePlayerClicke`
- used in `ComputerSetupScreen.kt:80`: `) } SetupPage(title = stringResource(R.string.One_Player)) { val minHeight = maxHeight`

## `Challenge` (string)
- English: Challenge
- used in `PlayMenuScreen.kt:85`: `val onePlayer = PlayButton(R.drawable.bouton_1_joueur, R.string.One_Player, PlayMenuAction.OnePlayerClicked) val analyse = PlayButton(R.drawable.bouton_analyse, R.string.Analyse_Games, PlayMenuAction.AnalyseGamesClicked)`

## `Joueur1` (string)
- English: Player 1
- used in `GameScreen.kt:187`: `// ApplicationTetrano's preference defaults: Joueur1/Joueur2, and User against the computer. val player1Name = uiModel.player1Name.ifBlank { stringResource(if (mode == GameMode.VS_COMPUTER) R.string.User else R.string.Jo`
- used in `TwoPlayersSetupScreen.kt:80`: `fun TwoPlayersSetupScreen(uiModel: TwoPlayersSetupUiModel, onAction: (TwoPlayersSetupAction) -> Unit) { val dimens = LocalLegacyDimens.current val player1Hint = uiModel.player1LastName.ifBlank { stringResource(R.string.J`

## `Joueur2` (string)
- English: Player 2
- used in `GameScreen.kt:189`: `stringResource(if (mode == GameMode.VS_COMPUTER) R.string.User else R.string.Joueur1) } val player2Name = uiModel.player2Name.ifBlank { stringResource(R.string.Joueur2) } if (title.isFinished) {`
- used in `TwoPlayersSetupScreen.kt:81`: `val dimens = LocalLegacyDimens.current val player1Hint = uiModel.player1LastName.ifBlank { stringResource(R.string.Joueur1) } val player2Hint = uiModel.player2LastName.ifBlank { stringResource(R.string.Joueur2) } val pla`

## `Level` (string)
- English: Level
- used in `ComputerSetupScreen.kt:135`: `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { FitText( text = stringResource(R.string.Level), maxFontSize = dimens.title2,`

## `Who_Begins` (string)
- English: Who begins?
- used in `ComputerSetupScreen.kt:115`: `val dimens = LocalLegacyDimens.current FitText( text = stringResource(R.string.Who_Begins), maxFontSize = dimens.title2,`

## `Enter_A_Name` (string)
- English: Enter a name
- used in `AnalyseGamesScreen.kt:211`: `text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.Enter_A_Name)) OutlinedTextField(`

## `Cancel` (string)
- English: Cancel
- used in `AnalyseGamesScreen.kt:117`: `}, dismissButton = { TextButton(onClick = { onAction(AnalyseGamesAction.DeleteDismissed) }) { Text(stringResource(R.string.Cancel)) } },`
- used in `AnalyseGamesScreen.kt:232`: `}, dismissButton = { TextButton(onClick = { onAction(AnalyseGamesAction.RenameDismissed) }) { Text(stringResource(R.string.Cancel)) } },`
- used in `GameLayout.kt:73`: `GameButtonSpec(stringResource(R.string.Mot_Clignoter), LegacyColors.ButtonGreen) { onAction(GameAction.BlinkClicked) } }, GameButtonSpec(stringResource(R.string.Cancel), LegacyColors.ButtonCyan) { onAction(GameAction.Can`

## `Settings_Saved` (string)
- English: Settings saved
- used in `SettingsScreen.kt:85`: `val uiModel by viewModel.uiState.collectAsState() val context = LocalContext.current val savedMessage = stringResource(R.string.Settings_Saved) val currentOnDone by rememberUpdatedState(onDone)`

## `Sentence_End_Null` (string)
- English: Game ended. No winner.
- used in `GameScreen.kt:192`: `if (title.isFinished) { return when { title.isDraw || title.winner == null -> stringResource(R.string.Sentence_End_Null) mode == GameMode.VS_COMPUTER -> {`

## `Sentence_End_Win` (string)
- English: Game ended. You won !
- used in `ChallengeScreen.kt:355`: `private fun endDialogTexts(dialog: ChallengeEndDialog): Triple<String, String, String> = when (dialog.situation) { ChallengeEndSituation.USER_WON -> Triple( stringResource(R.string.Sentence_End_Win), stringResource(R.str`
- used in `GameScreen.kt:194`: `title.isDraw || title.winner == null -> stringResource(R.string.Sentence_End_Null) mode == GameMode.VS_COMPUTER -> { if (title.winner == 1) stringResource(R.string.Sentence_End_Win) else stringResource(R.string.Sentence_`

## `Sentence_End_Lost` (string)
- English: Game ended. You lost.
- used in `ChallengeScreen.kt:360`: `) ChallengeEndSituation.COMPUTER_WON -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Computer_Won_Before),`
- used in `ChallengeScreen.kt:365`: `) ChallengeEndSituation.RAN_OUT_OF_RINGS -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Sentence_Number_Rings),`
- used in `GameScreen.kt:194`: `title.isDraw || title.winner == null -> stringResource(R.string.Sentence_End_Null) mode == GameMode.VS_COMPUTER -> { if (title.winner == 1) stringResource(R.string.Sentence_End_Win) else stringResource(R.string.Sentence_`

## `Sentence_Begin_Start` (string)
- English: Beginning: You can start to play.
- used in `GameScreen.kt:204`: `return when (mode) { GameMode.VS_COMPUTER -> when { title.playedCount == 0 && title.nextPlayer == 1 -> stringResource(R.string.Sentence_Begin_Start) title.nextPlayer == 1 -> stringResource(R.string.game_your_turn, player`

## `Sentence_Computer_Work` (string)
- English: Computer is playing.
- used in `GameScreen.kt:206`: `title.playedCount == 0 && title.nextPlayer == 1 -> stringResource(R.string.Sentence_Begin_Start) title.nextPlayer == 1 -> stringResource(R.string.game_your_turn, player1Name) else -> stringResource(R.string.Sentence_Comp`

## `Computer` (string)
- English: Computer
- used in `ComputerSetupScreen.kt:127`: `) WhoBeginsRadio( text = stringResource(R.string.Computer), selected = !uiModel.userBegins,`

## `Mot_Clignoter` (string)
- English: Blink
- used in `GameLayout.kt:71`: `GameButtonSpec(stringResource(R.string.Review_Game), LegacyColors.ButtonGreen) { onAction(GameAction.ReviewClicked) } } else { GameButtonSpec(stringResource(R.string.Mot_Clignoter), LegacyColors.ButtonGreen) { onAction(G`
- used in `GameScreen.kt:176`: `item(R.string.Cancel) { onAction(GameAction.CancelClicked) } item(R.string.Play_Again) { onAction(GameAction.PlayAgainClicked) } item(R.string.Mot_Clignoter) { onAction(GameAction.BlinkClicked) } if (mode == GameMode.VS_`

## `Abandon_Challenge` (string)
- English: Give up
- used in `ChallengeScreen.kt:291`: `private fun GiveUpButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) { LegacyButton( text = stringResource(R.string.Abandon_Challenge), onClick = onClick,`

## `Quit` (string)
- English: Quit
- used in `ChallengeScreen.kt:345`: `}, dismissButton = { TextButton(onClick = { onAction(ChallengeAction.EndDialogQuitClicked) }) { Text(stringResource(R.string.Quit)) } },`

## `Abandoned` (string)
- English: Abandoned
- used in `ChallengeScreen.kt:370`: `) ChallengeEndSituation.ABANDONED -> Triple( stringResource(R.string.Abandoned), stringResource(R.string.challenge_level_lost, dialog.level),`

## `Try_Again` (string)
- English: Try again
- used in `ChallengeScreen.kt:362`: `stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Computer_Won_Before), stringResource(R.string.Try_Again), )`
- used in `ChallengeScreen.kt:367`: `stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Sentence_Number_Rings), stringResource(R.string.Try_Again), )`
- used in `ChallengeScreen.kt:372`: `stringResource(R.string.Abandoned), stringResource(R.string.challenge_level_lost, dialog.level), stringResource(R.string.Try_Again), )`

## `Go_To_Next_Level` (string)
- English: Go to next level
- used in `ChallengeScreen.kt:357`: `stringResource(R.string.Sentence_End_Win), stringResource(R.string.challenge_level_passed, dialog.level), stringResource(R.string.Go_To_Next_Level), )`

## `Computer_Won_Before` (string)
- English: The computer has won before you!
- used in `ChallengeScreen.kt:361`: `ChallengeEndSituation.COMPUTER_WON -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Computer_Won_Before), stringResource(`

## `Sentence_Number_Rings` (string)
- English: You have not managed to use the allotted number of rings.
- used in `ChallengeScreen.kt:366`: `ChallengeEndSituation.RAN_OUT_OF_RINGS -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Sentence_Number_Rings), stringRes`

## `Rings_used` (string)
- English: Rings used:
- used in `ChallengeScreen.kt:272`: `) { FitText( text = stringResource(R.string.Rings_used), maxFontSize = LargeTextSize,`

## `Explanations_Challenge1` (string)
- English: Welcome to Challenge mode.\n\nEach level starts from a game already in progress, and you must win using no more than a set number of rings. Win within that number and you move on to the next level; otherwise you lose and can try again.
- used in `ChallengeScreen.kt:320`: `onDismissRequest = {}, title = { Text(stringResource(R.string.explanations_page, 1, 2)) }, text = { DialogText(stringResource(R.string.Explanations_Challenge1)) }, confirmButton = {`

## `Hint` (string)
- English: Hint
- used in `ChallengeScreen.kt:162`: `) DropdownMenuItem( text = { Text(stringResource(R.string.Hint)) }, onClick = {`

## `Explanations_Challenge2` (string)
- English: If a level seems too difficult, open the menu and choose Hint: the computer plays a move that leads towards the win for you. Hints are free and unlimited.
- used in `ChallengeScreen.kt:328`: `onDismissRequest = { onAction(ChallengeAction.ExplanationsDismissed) }, title = { Text(stringResource(R.string.explanations_page, 2, 2)) }, text = { DialogText(stringResource(R.string.Explanations_Challenge2)) }, confirm`

## `Next` (string)
- English: Next
- used in `AnalysisScreen.kt:290`: `) IconButton(onClick = { onAction(AnalysisAction.NextClicked) }, enabled = uiModel.cursor < uiModel.lineLength) { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.Next)) }`
- used in `ChallengeScreen.kt:322`: `text = { DialogText(stringResource(R.string.Explanations_Challenge1)) }, confirmButton = { TextButton(onClick = { onAction(ChallengeAction.ExplanationsNextClicked) }) { Text(stringResource(R.string.Next)) } },`

## `Explanations` (string)
- English: Explanations
- used in `ChallengeScreen.kt:155`: `) DropdownMenuItem( text = { Text(stringResource(R.string.Explanations)) }, onClick = {`
- used in `PuzzleScreen.kt:87`: `} IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.Explanations)) }`

## `All_Levels_Passed` (string)
- English: You have passed all the levels. You have finished the challenge mode.
- used in `ChallengeScreen.kt:80`: `val uiModel by viewModel.uiState.collectAsState() val context = LocalContext.current val allLevelsPassedMessage = stringResource(R.string.All_Levels_Passed) LaunchedEffect(Unit) {`

## `Sentence_No_Saved_Game` (string)
- English: You haven’t saved a game yet.
- used in `AnalysisScreen.kt:118`: `if (uiModel.notFound) { Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.Sentence_No_Saved_Game), color = BodyColor) }`

## `Delete` (string)
- English: Delete
- used in `AnalyseGamesScreen.kt:114`: `text = { Text(stringResource(R.string.Sentence_Confirm_Delete_Game)) }, confirmButton = { TextButton(onClick = { onAction(AnalyseGamesAction.DeleteConfirmed) }) { Text(stringResource(R.string.Delete)) } },`
- used in `AnalyseGamesScreen.kt:193`: `) DropdownMenuItem( text = { Text(stringResource(R.string.Delete)) }, onClick = {`

## `Rename` (string)
- English: Rename
- used in `AnalyseGamesScreen.kt:186`: `DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { DropdownMenuItem( text = { Text(stringResource(R.string.Rename)) }, onClick = {`

## `Confirm` (string)
- English: Confirm
- used in `AnalyseGamesScreen.kt:229`: `onClick = { onAction(AnalyseGamesAction.RenameConfirmed) }, enabled = draft.isNotBlank(), ) { Text(stringResource(R.string.Confirm)) } },`

## `Rename_A_Saved_Game` (string)
- English: Rename a saved game
- used in `AnalyseGamesScreen.kt:208`: `AlertDialog( onDismissRequest = { onAction(AnalyseGamesAction.RenameDismissed) }, title = { Text(stringResource(R.string.Rename_A_Saved_Game)) }, text = {`

## `Sentence_Confirm_Delete_Game` (string)
- English: Do you really want to delete this saved game?
- used in `AnalyseGamesScreen.kt:112`: `AlertDialog( onDismissRequest = { onAction(AnalyseGamesAction.DeleteDismissed) }, text = { Text(stringResource(R.string.Sentence_Confirm_Delete_Game)) }, confirmButton = {`

## `Continue_In_Two_Players_Mode` (string)
- English: Continue in 2 players mode
- used in `AnalysisScreen.kt:155`: `) DropdownMenuItem( text = { Text(stringResource(R.string.Continue_In_Two_Players_Mode)) }, onClick = {`

## `Continue_Against_Computer` (string)
- English: Continue against computer
- used in `AnalysisScreen.kt:148`: `DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { DropdownMenuItem( text = { Text(stringResource(R.string.Continue_Against_Computer)) }, onClick = {`

## `previous` (string)
- English: Previous
- used in `AnalysisScreen.kt:280`: `} IconButton(onClick = { onAction(AnalysisAction.PreviousClicked) }, enabled = uiModel.cursor > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.previous)) }`

## `name_already_used` (string)
- English: A saved game already has this name.
- used in `AnalyseGamesScreen.kt:217`: `isError = error, supportingText = if (error) { { Text(stringResource(R.string.name_already_used)) } } else {`

## `Play_Again` (string)
- English: Play again
- used in `GameLayout.kt:67`: `val dimens = LocalLegacyDimens.current val buttons = listOf( GameButtonSpec(stringResource(R.string.Play_Again), LegacyColors.ButtonYellow) { onAction(GameAction.PlayAgainClicked) }, if (finished) {`
- used in `GameScreen.kt:175`: `} item(R.string.Cancel) { onAction(GameAction.CancelClicked) } item(R.string.Play_Again) { onAction(GameAction.PlayAgainClicked) } item(R.string.Mot_Clignoter) { onAction(GameAction.BlinkClicked) }`

## `Internal_Error` (string)
- English: Internal Error. Sorry.
- used in `ChallengeScreen.kt:91`: `if (uiModel.showInternalError) { LaunchedEffect(Unit) { Toast.makeText(context, R.string.Internal_Error, Toast.LENGTH_LONG).show() viewModel.onAction(ChallengeAction.InternalErrorShown)`
- used in `GameScreen.kt:103`: `if (uiModel.showInternalError) { LaunchedEffect(Unit) { Toast.makeText(context, R.string.Internal_Error, Toast.LENGTH_LONG).show() onAction(GameAction.InternalErrorShown)`

## `Joueur1_Name` (string)
- English: Player 1’s name
- used in `TwoPlayersSetupScreen.kt:84`: `val players = listOf( PlayerSetup( label = stringResource(R.string.Joueur1_Name), name = uiModel.player1Name,`

## `Joueur2_Name` (string)
- English: Player 2’s name
- used in `TwoPlayersSetupScreen.kt:93`: `), PlayerSetup( label = stringResource(R.string.Joueur2_Name), name = uiModel.player2Name,`

## `Review_Game` (string)
- English: Review the game
- used in `GameLayout.kt:69`: `GameButtonSpec(stringResource(R.string.Play_Again), LegacyColors.ButtonYellow) { onAction(GameAction.PlayAgainClicked) }, if (finished) { GameButtonSpec(stringResource(R.string.Review_Game), LegacyColors.ButtonGreen) { o`

## `Analysing` (string)
- English: Analysing…
- used in `AnalysisScreen.kt:177`: `val status = when { uiModel.gameOver -> stringResource(R.string.analysis_game_over) live == null -> stringResource(R.string.Analysing) live.provenWinner != null -> provenText(live.provenWinner)`

## `Draw_Short` (string)
- English: Draw
- used in `AnalyseGamesScreen.kt:167`: `Text(stringResource(R.string.analysis_result_wins), fontSize = 13.sp, color = HintColor) } GameResult.DRAW -> Text(stringResource(R.string.Draw_Short), fontSize = 13.sp, color = HintColor) GameResult.UNFINISHED -> Text(s`
- used in `AnalysisScreen.kt:319`: `@Composable private fun OutlookBar(outlook: Outlook, player1Color: Color, player2Color: Color) { val drawLabel = stringResource(R.string.Draw_Short) Row(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCorner`

## `Verdict_Best` (string)
- English: Best move
- used in `AnalysisScreen.kt:341`: ` private fun MoveVerdict.label(): Int = when (this) { MoveVerdict.BEST -> R.string.Verdict_Best MoveVerdict.GOOD -> R.string.Verdict_Good`

## `Verdict_Good` (string)
- English: Good move
- used in `AnalysisScreen.kt:342`: `private fun MoveVerdict.label(): Int = when (this) { MoveVerdict.BEST -> R.string.Verdict_Best MoveVerdict.GOOD -> R.string.Verdict_Good MoveVerdict.INACCURACY -> R.string.Verdict_Inaccuracy`

## `Verdict_Inaccuracy` (string)
- English: Inaccuracy
- used in `AnalysisScreen.kt:343`: `MoveVerdict.BEST -> R.string.Verdict_Best MoveVerdict.GOOD -> R.string.Verdict_Good MoveVerdict.INACCURACY -> R.string.Verdict_Inaccuracy MoveVerdict.MISTAKE -> R.string.Verdict_Mistake`

## `Verdict_Mistake` (string)
- English: Mistake
- used in `AnalysisScreen.kt:344`: `MoveVerdict.GOOD -> R.string.Verdict_Good MoveVerdict.INACCURACY -> R.string.Verdict_Inaccuracy MoveVerdict.MISTAKE -> R.string.Verdict_Mistake MoveVerdict.BLUNDER -> R.string.Verdict_Blunder`

## `Verdict_Blunder` (string)
- English: Blunder
- used in `AnalysisScreen.kt:345`: `MoveVerdict.INACCURACY -> R.string.Verdict_Inaccuracy MoveVerdict.MISTAKE -> R.string.Verdict_Mistake MoveVerdict.BLUNDER -> R.string.Verdict_Blunder MoveVerdict.MISSED_WIN -> R.string.Verdict_Missed_Win`

## `Verdict_Missed_Win` (string)
- English: Missed a forced win
- used in `AnalysisScreen.kt:346`: `MoveVerdict.MISTAKE -> R.string.Verdict_Mistake MoveVerdict.BLUNDER -> R.string.Verdict_Blunder MoveVerdict.MISSED_WIN -> R.string.Verdict_Missed_Win MoveVerdict.ALLOWED_WIN -> R.string.Verdict_Allowed_Win`

## `Verdict_Allowed_Win` (string)
- English: Allowed a forced win
- used in `AnalysisScreen.kt:347`: `MoveVerdict.BLUNDER -> R.string.Verdict_Blunder MoveVerdict.MISSED_WIN -> R.string.Verdict_Missed_Win MoveVerdict.ALLOWED_WIN -> R.string.Verdict_Allowed_Win }`

## `Previous_Mistake` (string)
- English: Previous mistake
- used in `AnalysisScreen.kt:262`: `onClick = { onAction(AnalysisAction.PreviousMistakeClicked) }, enabled = review.hasPreviousMistake, ) { Text("◀ ${stringResource(R.string.Previous_Mistake)}") } TextButton(`

## `Next_Mistake` (string)
- English: Next mistake
- used in `AnalysisScreen.kt:266`: `onClick = { onAction(AnalysisAction.NextMistakeClicked) }, enabled = review.hasNextMistake, ) { Text("${stringResource(R.string.Next_Mistake)} ▶") } }`

## `Level_Neural` (string)
- English: neural network
- used in `ComputerSetupScreen.kt:149`: `if (uiModel.aiLevel >= AiService.NEURAL_LEVEL) { FitText( text = stringResource(R.string.Level_Neural), maxFontSize = dimens.title3,`

## `back` (string)
- English: Back
- used in `SettingsScreen.kt:113`: `navigationIcon = { IconButton(onClick = { onAction(SettingsAction.SaveClicked) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }`

## `settings_section_player` (string)
- English: Player
- used in `SettingsScreen.kt:158`: `private fun PlayerCard(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) { val focusManager = LocalFocusManager.current SettingsCard(stringResource(R.string.settings_section_player)) { OutlinedTextField(`

## `settings_section_colors` (string)
- English: Ring colors
- used in `SettingsScreen.kt:175`: `@Composable private fun ColorsCard(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) { SettingsCard(stringResource(R.string.settings_section_colors)) { ColorChoice(`

## `settings_your_rings` (string)
- English: Your rings
- used in `SettingsScreen.kt:177`: `SettingsCard(stringResource(R.string.settings_section_colors)) { ColorChoice( label = stringResource(R.string.settings_your_rings), selected = uiModel.userColor,`

## `settings_opponent_rings` (string)
- English: Opponent\'s rings
- used in `SettingsScreen.kt:183`: `) ColorChoice( label = stringResource(R.string.settings_opponent_rings), selected = uiModel.adversaryColor,`

## `settings_color_unavailable` (string)
- English: Used by the other player
- used in `SettingsScreen.kt:218`: `@Composable private fun Swatch(color: Color, name: String, isSelected: Boolean, isUnavailable: Boolean, onClick: () -> Unit, modifier: Modifier) { val unavailable = stringResource(R.string.settings_color_unavailable) Box`

## `settings_section_neural` (string)
- English: Level 101 (neural network)
- used in `SettingsScreen.kt:253`: `val index = choices.indexOfFirst { it >= uiModel.neuralSimulations }.takeIf { it >= 0 } ?: choices.lastIndex val format = NumberFormat.getIntegerInstance() SettingsCard(stringResource(R.string.settings_section_neural)) {`

## `settings_neural_simulations` (string)
- English: Maximum variations per move
- used in `SettingsScreen.kt:256`: `Row(verticalAlignment = Alignment.CenterVertically) { Text( stringResource(R.string.settings_neural_simulations), style = MaterialTheme.typography.bodyLarge,`

## `settings_neural_explanation` (string)
- English: The computer tests 64 variations, then twice as many each time the best move is not clear yet, up to this maximum. The higher it is, the stronger it plays in difficult positions, but the longer it can think. Default: %1$d.
- used in `SettingsScreen.kt:287`: `Spacer(Modifier.height(2.dp)) Text( stringResource(R.string.settings_neural_explanation, SettingsUiModel.DEFAULT_NEURAL_SIMULATIONS), style = MaterialTheme.typography.bodyMedium,`

## `Analyse_Games` (string)
- English: Analyse games
- used in `AnalyseGamesScreen.kt:83`: `containerColor = PageColor, contentWindowInsets = WindowInsets.safeDrawing, topBar = { LegacyTopBar(title = stringResource(R.string.Analyse_Games)) }, ) { padding ->`
- used in `PlayMenuScreen.kt:84`: `val twoPlayers = PlayButton(R.drawable.bouton_2_joueurs, R.string.Two_Players, PlayMenuAction.TwoPlayersClicked) val onePlayer = PlayButton(R.drawable.bouton_1_joueur, R.string.One_Player, PlayMenuAction.OnePlayerClicked`

## `Puzzles` (string)
- English: Puzzles
- used in `PlayMenuScreen.kt:86`: `val analyse = PlayButton(R.drawable.bouton_analyse, R.string.Analyse_Games, PlayMenuAction.AnalyseGamesClicked) val challenge = PlayButton(R.drawable.bouton_challenge, R.string.Challenge, PlayMenuAction.ChallengeClicked)`
- used in `PuzzleScreen.kt:77`: `topBar = { LegacyTopBar( title = stringResource(R.string.Puzzles), actions = {`
- used in `PuzzleScreen.kt:118`: `AlertDialog( onDismissRequest = { showHelp = false }, title = { Text(stringResource(R.string.Puzzles)) }, text = { Text(stringResource(R.string.puzzle_explanations)) },`

## `analysis_empty_board` (string)
- English: Empty board
- used in `AnalyseGamesScreen.kt:135`: `Spacer(Modifier.width(16.dp)) Column { Text(stringResource(R.string.analysis_empty_board), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) Text(stringResource(R.string.analysis_empty_board`
- used in `AnalysisScreen.kt:106`: `LegacyTopBar( title = uiModel.gameName.ifEmpty { stringResource(if (uiModel.freePlay) R.string.analysis_empty_board else R.string.analysis_title) },`

## `analysis_empty_board_hint` (string)
- English: Play both sides, the network analyses each position
- used in `AnalyseGamesScreen.kt:136`: `Column { Text(stringResource(R.string.analysis_empty_board), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) Text(stringResource(R.string.analysis_empty_board_hint), fontSize = 13.sp, colo`

## `analysis_no_games` (string)
- English: Your finished games will be saved here automatically.
- used in `AnalyseGamesScreen.kt:95`: `item(key = "none") { Text( stringResource(R.string.analysis_no_games), color = HintColor,`

## `analysis_rings_count` (string)
- English: Rings: %1$d
- used in `AnalyseGamesScreen.kt:159`: `) Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.analysis_rings_count, game.moves), fontSize = 13.sp, color = HintColor) Text("  ·  ", fontSize = 13.sp, color = HintColor)`

## `analysis_result_wins` (string)
- English: wins
- used in `AnalyseGamesScreen.kt:165`: `Box(Modifier.size(10.dp).clip(CircleShape).background(Color(game.winnerColor))) Spacer(Modifier.width(4.dp)) Text(stringResource(R.string.analysis_result_wins), fontSize = 13.sp, color = HintColor) }`

## `analysis_result_unfinished` (string)
- English: Unfinished
- used in `AnalyseGamesScreen.kt:168`: `} GameResult.DRAW -> Text(stringResource(R.string.Draw_Short), fontSize = 13.sp, color = HintColor) GameResult.UNFINISHED -> Text(stringResource(R.string.analysis_result_unfinished), fontSize = 13.sp, color = HintColor) `

## `analysis_title` (string)
- English: Analysis
- used in `AnalysisScreen.kt:106`: `LegacyTopBar( title = uiModel.gameName.ifEmpty { stringResource(if (uiModel.freePlay) R.string.analysis_empty_board else R.string.analysis_title) },`

## `analysis_game_over` (string)
- English: Game over
- used in `AnalysisScreen.kt:176`: `} val status = when { uiModel.gameOver -> stringResource(R.string.analysis_game_over) live == null -> stringResource(R.string.Analysing)`

## `analysis_variations` (string)
- English: %1$s variations tested
- used in `AnalysisScreen.kt:179`: `live == null -> stringResource(R.string.Analysing) live.provenWinner != null -> provenText(live.provenWinner) else -> stringResource(R.string.analysis_variations, format.format(live.simulations)) }`

## `analysis_best_move` (string)
- English: Dashed rings: the moves, more visible the more recommended (rank · chance of winning). Press the board to read a ring.
- used in `AnalysisScreen.kt:195`: `Box(Modifier.size(12.dp).clip(CircleShape).background(color.copy(alpha = 0.45f))) Spacer(Modifier.width(8.dp)) Text(stringResource(R.string.analysis_best_move), fontSize = 13.sp, color = HintColor) }`

## `analysis_forced_win` (string)
- English: Forced win
- used in `AnalysisScreen.kt:203`: `private fun provenText(winner: Int): String = when (winner) { 0 -> stringResource(R.string.analysis_forced_draw) else -> stringResource(R.string.analysis_forced_win) }`

## `analysis_forced_draw` (string)
- English: Forced draw
- used in `AnalysisScreen.kt:202`: `@Composable private fun provenText(winner: Int): String = when (winner) { 0 -> stringResource(R.string.analysis_forced_draw) else -> stringResource(R.string.analysis_forced_win)`

## `analysis_depth` (string)
- English: Limit: %1$s
- used in `AnalysisScreen.kt:214`: `AssistChip( onClick = { expanded = true }, label = { Text(stringResource(R.string.analysis_depth, format.format(depth)), fontSize = 12.sp) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, `

## `analysis_reviewing` (string)
- English: Reviewing the moves… %1$d %%
- used in `AnalysisScreen.kt:242`: `if (progress != null) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(stringResource(R.string.analysis_reviewing, (progress * 100).roundToInt()), fontSize = 13.sp, color = HintColor) LinearProgressIndic`

## `analysis_first` (string)
- English: First move
- used in `AnalysisScreen.kt:277`: `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) { IconButton(onClick = { onAction(AnalysisAction.FirstClicked) }, enabled = uiModel.cursor > 0) { Icon(Icons.Default.Fir`

## `analysis_last` (string)
- English: Last move
- used in `AnalysisScreen.kt:293`: `} IconButton(onClick = { onAction(AnalysisAction.LastClicked) }, enabled = uiModel.cursor < uiModel.lineLength) { Icon(Icons.Default.LastPage, contentDescription = stringResource(R.string.analysis_last)) }`

## `analysis_variation_from` (string)
- English: Variation from move %1$d
- used in `AnalysisScreen.kt:304`: `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) { Text( stringResource(R.string.analysis_variation_from, variation.fromMove), fontSiz`

## `analysis_back_to_game` (string)
- English: Back to the game
- used in `AnalysisScreen.kt:310`: `) FilledTonalButton(onClick = { onAction(AnalysisAction.BackToGameClicked) }) { Text(stringResource(R.string.analysis_back_to_game), fontSize = 13.sp) }`

## `analysis_play_hint` (string)
- English: Drag a ring to try a move, for either side.
- used in `AnalysisScreen.kt:134`: `Navigation(uiModel, onAction) if (uiModel.lineLength == 0 || (uiModel.variation == null && uiModel.cursor == uiModel.lineLength && !uiModel.gameOver)) { Text(stringResource(R.string.analysis_play_hint), fontSize = 13.sp,`

## `puzzle_correct` (string)
- English: Correct! Keep going.
- used in `PuzzleScreen.kt:105`: `PuzzlePhase.FAILED -> Outcome(uiModel, success = false, onAction) else -> if (uiModel.progressing) { Text(stringResource(R.string.puzzle_correct), color = SuccessColor, fontWeight = FontWeight.SemiBold) }`

## `puzzle_stats` (string)
- English: Solved: %1$d out of %2$d
- used in `PuzzleScreen.kt:109`: `} Text( stringResource(R.string.puzzle_stats, uiModel.solved, uiModel.attempts), fontSize = 13.sp,`

## `puzzle_solved` (string)
- English: Solved!
- used in `PuzzleScreen.kt:143`: `private fun Outcome(uiModel: PuzzleUiModel, success: Boolean, onAction: (PuzzleAction) -> Unit) { Text( stringResource(if (success) R.string.puzzle_solved else R.string.puzzle_failed), style = MaterialTheme.typography.ti`

## `puzzle_failed` (string)
- English: Not a winning move
- used in `PuzzleScreen.kt:143`: `private fun Outcome(uiModel: PuzzleUiModel, success: Boolean, onAction: (PuzzleAction) -> Unit) { Text( stringResource(if (success) R.string.puzzle_solved else R.string.puzzle_failed), style = MaterialTheme.typography.ti`

## `puzzle_solution_shown` (string)
- English: A winning move is shown as a translucent ring.
- used in `PuzzleScreen.kt:148`: `color = if (success) SuccessColor else FailureColor, ) if (!success) Text(stringResource(R.string.puzzle_solution_shown), fontSize = 14.sp, color = BodyColor) val delta = uiModel.ratingDelta`

## `puzzle_rating_change` (string)
- English: Your rating: %1$d (%2$s)
- used in `PuzzleScreen.kt:153`: `val sign = if (delta >= 0) "+" else "−" Text( stringResource(R.string.puzzle_rating_change, uiModel.rating, "$sign${kotlin.math.abs(delta)}"), fontSize = 14.sp,`

## `puzzle_difficulty` (string)
- English: Puzzle rating: %1$d
- used in `PuzzleScreen.kt:158`: `) } uiModel.puzzleRating?.let { Text(stringResource(R.string.puzzle_difficulty, it), fontSize = 13.sp, color = HintColor) } Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {`

## `puzzle_retry` (string)
- English: Retry
- used in `PuzzleScreen.kt:162`: `if (!success) { OutlinedButton(onClick = { onAction(PuzzleAction.RetryClicked) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.puzzle_retry)) }`

## `puzzle_next` (string)
- English: Next puzzle
- used in `PuzzleScreen.kt:169`: `colors = ButtonDefaults.buttonColors(containerColor = AccentColor), modifier = Modifier.weight(1f), ) { Text(stringResource(R.string.puzzle_next)) } }`

## `puzzle_retry_note` (string)
- English: Retries do not change your rating.
- used in `PuzzleScreen.kt:171`: `) { Text(stringResource(R.string.puzzle_next)) } } if (!success) Text(stringResource(R.string.puzzle_retry_note), fontSize = 12.sp, color = HintColor) }`

## `puzzle_explanations` (string)
- English: Each puzzle is a position from a game where you can force a win: find the winning moves, the opponent defends as well as possible. A wrong move ends the attempt.\n\nYou and the puzzles have an Elo rating: puzzles are chosen so that you solve about 3 out of 4, and your rating follows your results (only the first attempt at a puzzle counts).
- used in `PuzzleScreen.kt:119`: `onDismissRequest = { showHelp = false }, title = { Text(stringResource(R.string.Puzzles)) }, text = { Text(stringResource(R.string.puzzle_explanations)) }, confirmButton = { TextButton(onClick = { showHelp = false }) { T`

## `puzzle_find_win` (plurals)
- English: <item quantity="one">Win in %d move</item> <item quantity="other">Win in %d moves</item>
- used in `PuzzleScreen.kt:132`: `Spacer(Modifier.width(10.dp)) Text( pluralStringResource(R.plurals.puzzle_find_win, uiModel.movesToWin, uiModel.movesToWin), style = MaterialTheme.typography.titleMedium,`

## `game_your_turn` (string)
- English: %1$s, it’s your turn.
- used in `GameScreen.kt:205`: `GameMode.VS_COMPUTER -> when { title.playedCount == 0 && title.nextPlayer == 1 -> stringResource(R.string.Sentence_Begin_Start) title.nextPlayer == 1 -> stringResource(R.string.game_your_turn, player1Name) else -> string`
- used in `GameScreen.kt:213`: `stringResource(R.string.game_you_can_start, name) } else { stringResource(R.string.game_your_turn, name) }`

## `game_you_can_start` (string)
- English: %1$s, you can start.
- used in `GameScreen.kt:211`: `val name = if (title.nextPlayer == 1) player1Name else player2Name if (title.playedCount == 0) { stringResource(R.string.game_you_can_start, name) } else {`

## `game_winner` (string)
- English: Winner: %1$s
- used in `GameScreen.kt:198`: `else -> { val winnerName = if (title.winner == 1) player1Name else player2Name stringResource(R.string.game_winner, winnerName) }`

## `challenge_title` (string)
- English: Challenge · Level %1$d
- used in `ChallengeScreen.kt:118`: `LegacyTopBar( // ChallengeActivity.updateTitle() ("%s      %s %d"), as one translatable sentence. title = stringResource(R.string.challenge_title, uiModel.level), actions = { ChallengeMenu(onAction = onAction, onNavigate`

## `explanations_page` (string)
- English: Explanations %1$d/%2$d
- used in `ChallengeScreen.kt:319`: `1 -> AlertDialog( onDismissRequest = {}, title = { Text(stringResource(R.string.explanations_page, 1, 2)) }, text = { DialogText(stringResource(R.string.Explanations_Challenge1)) },`
- used in `ChallengeScreen.kt:327`: `2 -> AlertDialog( onDismissRequest = { onAction(ChallengeAction.ExplanationsDismissed) }, title = { Text(stringResource(R.string.explanations_page, 2, 2)) }, text = { DialogText(stringResource(R.string.Explanations_Chall`

## `challenge_level_passed` (string)
- English: You passed level %1$d.
- used in `ChallengeScreen.kt:356`: `ChallengeEndSituation.USER_WON -> Triple( stringResource(R.string.Sentence_End_Win), stringResource(R.string.challenge_level_passed, dialog.level), stringResource(R.string.Go_To_Next_Level),`

## `challenge_level_lost` (string)
- English: You lost at level %1$d.
- used in `ChallengeScreen.kt:361`: `ChallengeEndSituation.COMPUTER_WON -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Computer_Won_Before), stringResource(`
- used in `ChallengeScreen.kt:366`: `ChallengeEndSituation.RAN_OUT_OF_RINGS -> Triple( stringResource(R.string.Sentence_End_Lost), stringResource(R.string.challenge_level_lost, dialog.level) + "\n" + stringResource(R.string.Sentence_Number_Rings), stringRes`
- used in `ChallengeScreen.kt:371`: `ChallengeEndSituation.ABANDONED -> Triple( stringResource(R.string.Abandoned), stringResource(R.string.challenge_level_lost, dialog.level), stringResource(R.string.Try_Again),`

## `challenge_rings_info` (string)
- English: In Challenge mode, you have to win with a limited number of rings.\n\nIn this level:\n• rings allowed: %1$d\n• rings used: %2$d
- used in `ChallengeScreen.kt:308`: `onDismissRequest = { onAction(ChallengeAction.RingsInfoDismissed) }, text = { DialogText(stringResource(R.string.challenge_rings_info, uiModel.authorizedRings, uiModel.usedRings)) },`

## `analysis_ring_position` (string)
- English: Ring %1$d / %2$d
- used in `AnalysisScreen.kt:283`: `} Text( stringResource(R.string.analysis_ring_position, uiModel.cursor, uiModel.lineLength), textAlign = TextAlign.Center,`

## `analysis_ring_verdict` (string)
- English: Ring %1$d: %2$s
- used in `AnalysisScreen.kt:249`: `val color = if (review.lastMovePlayer == 1) uiModel.board.player1Color else uiModel.board.player2Color Text( text = stringResource(R.string.analysis_ring_verdict, uiModel.cursor, stringResource(verdict.label())), color =`

## `color_gray` (string)
- English: Gray
- used in `Color.kt:27`: `/** The name of each [selectable] color, in the same order, for screen readers. */ val selectableNames: List<Int> = listOf( R.string.color_gray, R.string.color_black,`

## `color_black` (string)
- English: Black
- used in `Color.kt:28`: `val selectableNames: List<Int> = listOf( R.string.color_gray, R.string.color_black, R.string.color_brown,`

## `color_brown` (string)
- English: Brown
- used in `Color.kt:29`: `R.string.color_gray, R.string.color_black, R.string.color_brown, R.string.color_orange,`

## `color_orange` (string)
- English: Orange
- used in `Color.kt:30`: `R.string.color_black, R.string.color_brown, R.string.color_orange, R.string.color_red,`

## `color_red` (string)
- English: Red
- used in `Color.kt:31`: `R.string.color_brown, R.string.color_orange, R.string.color_red, R.string.color_purple,`

## `color_purple` (string)
- English: Purple
- used in `Color.kt:32`: `R.string.color_orange, R.string.color_red, R.string.color_purple, R.string.color_blue,`

## `color_blue` (string)
- English: Blue
- used in `Color.kt:33`: `R.string.color_red, R.string.color_purple, R.string.color_blue, R.string.color_green,`

## `color_green` (string)
- English: Green
- used in `Color.kt:34`: `R.string.color_purple, R.string.color_blue, R.string.color_green, )`

## `about_version` (string)
- English: Version %1$s
- used in `AboutScreen.kt:81`: `fontWeight = FontWeight.Bold, ) Text(stringResource(R.string.about_version, versionName), color = TetranoPalette.TextSecondary) }`

## `about_qubic_title` (string)
- English: Qubic
- used in `AboutScreen.kt:84`: `} } Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano))`

## `about_qubic` (string)
- English: Qubic is tic-tac-toe in three dimensions: a cube of 4 × 4 × 4 cells, that is 64 cells and 76 possible lines. The first player to line up four pieces of their color wins. Sold as a board game in the 1960s, it was solved in 1980 by Oren Patashnik: the first player can always force a win.
- used in `AboutScreen.kt:84`: `} } Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano))`

## `about_tetrano_title` (string)
- English: Tetrano
- used in `AboutScreen.kt:85`: `} Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.ab`

## `about_tetrano` (string)
- English: Tetrano is the two-dimensional representation of Qubic invented by Maxime Vial in 2004, which this app uses: the four levels of the cube become four sizes of rings, placed on a 4 × 4 board. A line then reads as four concentric rings in one square, or as four rings in a row, all the same size or in increasing or decreasing size.
- used in `AboutScreen.kt:85`: `} Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.ab`

## `about_app_title` (string)
- English: The app
- used in `AboutScreen.kt:86`: `Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.abou`

## `about_app` (string)
- English: This app was written by Thibaut PERRIN. Started in 2014, it was brought up to date in 2026 with the help of artificial intelligence: a complete rewrite, a new computer player based on a neural network, puzzles and game analysis.
- used in `AboutScreen.kt:86`: `Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic)) Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.abou`

## `about_open_source_title` (string)
- English: Open source
- used in `AboutScreen.kt:87`: `Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.about_app_title), stringResource(R.string.about_app)) Section(stringResource(R.string.about_op`

## `about_open_source` (string)
- English: The source code of Tetrano is open source, under the MIT license, and available on GitHub.
- used in `AboutScreen.kt:87`: `Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano)) Section(stringResource(R.string.about_app_title), stringResource(R.string.about_app)) Section(stringResource(R.string.about_op`

## `about_github` (string)
- English: See the code on GitHub
- used in `AboutScreen.kt:89`: `Section(stringResource(R.string.about_open_source_title), stringResource(R.string.about_open_source)) LegacyButton( text = stringResource(R.string.about_github), onClick = { runCatching { uriHandler.openUri(GITHUB_URL) }`

## `about_license` (string)
- English: MIT license
- used in `AboutScreen.kt:95`: `) TextButton(onClick = { runCatching { uriHandler.openUri(LICENSE_URL) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.about_license), color = TetranoPalette.Accent) }`

## `more_options` (string)
- English: More options
- used in `AnalysisScreen.kt:144`: `var expanded by rememberSaveable { mutableStateOf(false) } IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options)) }`
- used in `ChallengeScreen.kt:144`: `var menuExpanded by remember { mutableStateOf(false) } IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options)) }`
- used in `GameScreen.kt:161`: `var expanded by remember { mutableStateOf(false) } IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options)) }`

## `game_options` (string)
- English: Options for %1$s
- used in `AnalyseGamesScreen.kt:182`: `Box { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.game_options, name), tint = HintColor) }`

## `level_lower` (string)
- English: Lower level
- used in `ComputerSetupScreen.kt:160`: `next = false, onClick = { onAction(ComputerSetupAction.LevelDecremented) }, contentDescription = stringResource(R.string.level_lower), modifier = Modifier.widthIn(min = MIN_TOUCH_TARGET).width(dimens.mediumMargin),`

## `level_higher` (string)
- English: Higher level
- used in `ComputerSetupScreen.kt:176`: `next = true, onClick = { onAction(ComputerSetupAction.LevelIncremented) }, contentDescription = stringResource(R.string.level_higher), modifier = Modifier.widthIn(min = MIN_TOUCH_TARGET).width(dimens.mediumMargin),`

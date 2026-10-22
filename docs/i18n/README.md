# Translating Tetrano

Strings live in `app/src/main/res/values*/strings.xml` (`values/` is the English source; `values-fr`,
`-de`, `-es`, `-it`, `-ru` are the translations). [`strings-context.md`](strings-context.md) says,
for every string, where the code uses it — a button, a dialog title, a screen title, a screen-reader
label, a sentence with placeholders… — so a translation can fit its place.

## What the app is

Tetrano is a two-player board game, the ring version of Qubic (3D tic-tac-toe on 4 × 4 × 4 cells).
Players take turns putting a **ring** (4 sizes) on a 4 × 4 **board**; four rings of one color in a
line win. Screens: home, play menu, 2-player and vs-computer setup, game, **Challenge** (245 levels
with a limited number of rings, free **hints**), **Puzzles** (rated with **Elo**), **Analyse games**
(a neural network gives a verdict on each move, tests **variations**), settings, rules, About.
"Level 101" is the neural-network computer player; levels 0–100 are the classic one.

## Glossary — keep these consistent inside a language

| English | Meaning |
|---|---|
| ring | one of the pieces; 4 sizes, small to large |
| board / square | the 4 × 4 grid / one of its 16 cells |
| line / four-in-a-row | 4 rings of the same color that win: concentric in a square, or aligned |
| concentric | 4 rings of different sizes in the same square |
| Challenge | the 245-level mode (keep the word if the language uses it naturally) |
| hint | the free help in Challenge |
| puzzle | a position with a forced win to find |
| variation | an alternative line of play the network tests during analysis |
| forced win | a win the opponent cannot prevent |
| blunder / mistake / inaccuracy | the three grades of a bad move, from worst to mildest |
| neural network | the level-101 computer player |

## Rules for translators

- Keep every placeholder (`%1$d`, `%1$s`, `%2$d`, `%%`) and `\n`; do not reorder placeholders
  unless the grammar needs it (positional `%1$s` / `%2$s` make that safe).
- Mind the room: button labels and top-bar titles are short (a few words); a long translation
  shrinks the text. Dialog and description texts can be longer.
- Plurals (`<plurals>`): provide every CLDR category the language needs (Russian: one, few, many,
  other; French/Spanish/Italian: one, many, other).
- Apostrophes: write the typographic `’` (U+2019) — a straight `'` must be escaped as `\'` in
  Android XML.
- Informal or formal address: one choice per language, used throughout (see each file).
- `app_name` is not translated.

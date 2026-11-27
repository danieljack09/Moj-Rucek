# Мој Ручек redesign spec

Source of truth for the visual redesign. Reference images are in `docs/redesign/reference/`.
The redesign is VISUAL ONLY: no behaviour, data, navigation route or text changes.

## Reference images

| File | Shows |
| --- | --- |
| 01-home-hero.png | Home screen close-up: title, search pill + filter button, recipe cards, promo card, floating nav |
| 02-app-icon.png | App icon: white line chef hat with flame on green rounded square |
| 03-store-and-notification.png | Icon in context, notification style |
| 04-sign-in-registration.png | Sign in, registration, password recovery, field error states |
| 05-user-flow.png | Information architecture of the reference app (context only) |
| 06-main-page-search-filters.png | Home, search with recent list, recipe grid, filter bottom sheet, sort sheet |
| 07-recipe-page.png | Recipe detail, cooking steps, rate sheet, reviews |
| 08-customer-journey.png | Research notes (context only) |
| 09-favourites-grocery-list.png | Favourites grid, grocery list rows, edit menu, edit bottom sheet, select mode |
| 10-design-system.png | Palette, type scale, icons, buttons, fields, chips, nav bar |
| 11-profile.png | Profile list, personal info form, subscription, notes, categories |

## Colour tokens

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| accent | #F16F33 | #F2834F | Primary buttons, links, ticked checkboxes, hearts, active slider dots |
| accentSoft | #F7B899 | #7A4329 | Disabled primary button, error field fill |
| onAccent | #FFFFFF | #1C1D12 | Text/icons on accent |
| green | #ACBD6F | #8E9F55 | Promo cards, selected chips, cooking-step chips |
| onGreen | #1B1B1B | #1C1D12 | Text on green |
| greenContainer | #EAECCA | #33361F | Search, text fields, chips, list rows, nav bar pill, circle buttons |
| background | #FFFAE7 | #1C1D12 | Screen background, sheets, dialogs |
| card | #E3E5C4 | #2A2C1C | Recipe image wells, section cards |
| darkOlive | #434724 | #DDE0B8 | Active nav circle, selected outline, strong accents |
| onDarkOlive | #FFFAE7 | #1C1D12 | Icon on active nav circle |
| text | #1B1B1B | #F4F1DE | Titles, body |
| textSecondary | #6E6E5A | #B5B39A | Captions, meta, "See all", placeholders |
| divider | #D9DBB6 | #3D402A | Thin dividers inside cards |
| error | #D9481C | #FF8A65 | Error helper text and borders (use with accentSoft fill) |

Material 3 mapping: primary=accent, onPrimary=onAccent, primaryContainer=accentSoft, secondary=green,
secondaryContainer=greenContainer, onSecondaryContainer=text, tertiary=darkOlive, onTertiary=onDarkOlive,
background=surface=background, surfaceVariant=surfaceContainer*=greenContainer (surfaceContainerHigh=card),
onSurface=text, onSurfaceVariant=textSecondary, outline=darkOlive at 40%, outlineVariant=divider, error=error.
No dynamic colour. Status and navigation bars transparent (edge-to-edge) with dark icons in light mode.

## Type

Font: Inter (SIL OFL), Regular 400 / Medium 500 / SemiBold 600, bundled in `res/font`.
The reference's SF Pro Display cannot be shipped on Android; Inter is the stand-in. Playfair Display is not used.

| Role | Size / line | Weight |
| --- | --- | --- |
| displayLarge | 44 / 50 | Medium |
| headlineLarge (screen title) | 28 / 34 | Medium |
| headlineMedium (recipe title) | 24 / 30 | Medium |
| titleLarge (section title) | 22 / 28 | Medium |
| titleMedium (card title) | 17 / 22 | Medium |
| bodyLarge | 17 / 24 | Regular |
| bodyMedium | 15 / 21 | Regular |
| bodySmall | 13 / 18 | Regular |
| labelLarge (buttons) | 16 / 20 | Medium |
| labelMedium (chips, pills) | 13 / 16 | Medium |
| labelSmall (tiny meta) | 11 / 14 | Medium |

Titles are Medium, never Bold. Letter spacing -0.2sp on 22sp and above, 0 elsewhere.

## Shape and spacing

- Pill (fully rounded): buttons, search, text fields, chips, list rows, nav bar, meta pills.
- Cards 20dp, bottom sheets 28dp top corners, dialogs 28dp, recipe image well 20dp, small badges 8dp.
- No elevation or shadows anywhere except a very soft shadow under the floating nav bar (4dp, 8% black).
- Screen edge padding 20dp; 16dp between cards; 24dp between sections; 12dp inside rows; 20dp inside cards.
- Touch targets at least 48dp even when the visual is smaller.

## Components

1. **Floating nav bar**: greenContainer pill, 16dp from screen sides and bottom (plus system inset), 64dp tall,
   five 48dp circles; inactive = transparent with darkOlive line icon, active = darkOlive fill with onDarkOlive icon.
   No visible labels; labels stay as contentDescription.
2. **Buttons**: Primary = accent pill 52dp, onAccent labelLarge. Disabled = accentSoft fill. Secondary = greenContainer
   pill, accent label. Outline = 1dp accent border, accent label, transparent. Text link = accent labelLarge.
3. **Circle icon button**: 40dp greenContainer circle, 20dp darkOlive icon (48dp touch target).
4. **Search pill**: greenContainer, 48dp, leading accent search icon, placeholder textSecondary; trailing circle filter button beside it.
5. **Text field**: greenContainer pill 56dp, label inside as small top line (labelSmall textSecondary) once filled,
   placeholder centred vertically when empty, trailing eye/clear icon. Error: accentSoft fill at 40%, 1dp error border, error helper text below.
6. **Chip**: greenContainer pill 32dp, labelMedium. Selected (multi-select filter) = 1dp darkOlive outline;
   selected (single choice / tab) = green fill.
7. **Recipe card**: square card-colour well (20dp), dish photo cropped to a circle filling ~85% of it; heart circle button
   top-right (accent outline heart, filled when favourite); bottom-left meta pills (clock + "20 min", other existing meta);
   title below the well, titleMedium, max 2 lines. Without a photo: category icon on a green circle in the well.
8. **Promo card**: green fill, 20dp corners, title titleLarge, subtitle bodySmall, accent primary button,
   image or illustration bleeding off the right edge.
9. **Section header**: title (titleLarge) left, "See all"-style action (labelMedium text) right, only when an action exists.
10. **List row**: greenContainer pill/rounded row, 52dp min, leading checkbox or icon, trailing chevron/value/action.
    Checkbox = 20dp rounded square, 1.5dp darkOlive border; ticked = accent fill with white check.
11. **Bottom sheet**: background colour, 28dp top corners, grabber 36x4 textSecondary 40%, title headlineMedium + circle close,
    footer with Outline "clear/cancel" and Primary "apply/save" side by side.
12. **Dialog**: background colour, 28dp corners, titleLarge, bodyMedium, actions as pill buttons (Primary + text button).
13. **Meta pill**: background colour at 90% over image or greenContainer on cream, 24dp tall, accent 14dp icon + labelSmall.
14. **Icons**: outlined/rounded Material Symbols style, thin stroke; never filled except active states (heart, nav).

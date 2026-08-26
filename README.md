# Voice Note — dictate formatted legal documents on Android

A small, single-purpose Android dictation app: speak, format with your voice,
and email yourself a properly formatted **.docx** document (opens in
OnlyOffice, Word, LibreOffice and Google Docs). No account, no sign-in, no
subscription — recognition runs through the phone's built-in on-device speech
service.

## Voice commands

| Say                              | Result                                                        |
| -------------------------------- | ------------------------------------------------------------- |
| `paragraph` / `new paragraph`    | Ends the paragraph and skips a line                           |
| `heading`                        | Next text becomes a heading — **bold + CAPITALS** (end it with `paragraph`) |
| `quote` … `end quote` / `unquote`| Quotation block: “quotation marks”, *italics*, indented 2 cm  |
| `footnote` … `end footnote`      | Inserts a real Word footnote at the current position          |
| `new line`                       | Line break without a new paragraph                            |
| `full stop`, `comma`, `colon`, `semicolon`, `question mark`, `exclamation mark`, `open bracket`, `close bracket` | Punctuation |
| `end` (on its own)               | Finishes the document, exports the .docx and opens your email app with it attached |

Also automatic:

- **British / South African spelling** — common American spellings from the
  recogniser are corrected (colour, organisation, defence, licence, …), and the
  document language is set to `en-ZA` so the spellchecker in Word/OnlyOffice
  carries on from there.
- **Sentence capitalisation** after full stops.
- **Page numbers** in the document footer, A4 page size.

## Using it

1. Open the app; it asks once for the email address to send documents to
   (change it any time via **My email**).
2. Tap **Start dictating** and speak. The live preview shows the text
   formatted as it will appear in the document.
3. Say **"end"** (or tap **Finish & email document**). The .docx is generated
   and your email app opens with it attached to your address — one tap to send.

## Building

```bash
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew test              # unit tests (parser, spelling, docx writer)
```

CI builds the APK on every push (GitHub Actions → build artifacts).

## Architecture

- `SpeechEngine` — continuous dictation on Android's `SpeechRecognizer`
  (`EXTRA_PREFER_OFFLINE`, `en-ZA`); restarts itself across silence.
- `CommandParser` — token state machine turning utterances into a `Document`
  of heading / paragraph / quote blocks with anchored footnotes.
- `BritishSpelling` — curated US→UK/SA word map (no suffix guessing, so legal
  terms are never mangled).
- `DocxWriter` — dependency-free OOXML writer: styles, footnotes, footer with
  a `PAGE` field, 2 cm quote indents.
- `EmailSender` — exports to app storage and shares via the standard email
  intent (no SMTP credentials stored).

# NVict Reader (Android)

Android-versie van NVict Reader, de PDF-lezer van NVict Service. Native Kotlin/Jetpack Compose met de
Pdfium-engine (Apache 2.0, dus zonder licentiekosten geschikt voor de Play Store).

## Functies
- Beginscherm zoals Fill & Sign: grote openknop en recente bestanden met miniatuur, tijdstip en grootte
  ("Alles weergeven" voor de volledige lijst; lang indrukken om uit de lijst te halen).
- **Invulformulieren** (AcroForm): tekstvelden, vinkjes, keuzerondjes en keuzelijsten worden herkend en zijn tikbaar.
  Waarden worden in de echte velden opgeslagen, dus ook leesbaar en wijzigbaar in Acrobat. Dynamische XFA-formulieren
  worden gemeld maar kunnen niet ingevuld worden.
- Vaste **handtekening en paraaf** (pen-icoon op het beginscherm of in Bewerken): één keer tekenen, daarna met één tik
  plaatsen. Opnieuw tekenen of verwijderen kan altijd.
- Thema: Automatisch (volgt de telefoon), Licht of Donker — via ⋮ > Instellingen.
- PDF openen via de knop, vanuit Bestanden, mail, browser of de deelfunctie; registreert zich als PDF-viewer.
- Meerdere documenten tegelijk open (tabbladen), recente bestanden met onthouden pagina.
- Knijpzoom, dubbeltikken om te zoomen, horizontaal scrollen bij inzoomen, passend in breedte.
- Zoeken in het hele document met markeringen en vorige/volgende.
- Tekst selecteren: lang indrukken en slepen, daarna kopiëren of de hele pagina selecteren.
- Links in het document (web en interne verwijzingen), inhoudsopgave (bladwijzers), documentinfo.
- **Bewerken** (potloodknop): tekst toevoegen (lettergrootte en kleur), handtekening plaatsen (met de vinger tekenen,
  wordt bewaard voor hergebruik, verslepen en schalen) en markeren (tekst selecteren → Markeren, kleur aanpasbaar).
  Ongedaan maken, daarna Opslaan (overschrijft het origineel), Opslaan als… of Bewerkte versie delen. Bijlagen uit de
  mail zijn alleen-lezen; daar vraagt de app automatisch om een nieuwe locatie.
- Pagina's exporteren naar een nieuwe PDF (bijv. `1-3,5`), document delen, pagina delen als afbeelding, afdrukken.
- Licht/donker thema volgt Android; optioneel donkere pagina's.
- Beveiligde PDF's met wachtwoord.

## Bouwen
Elke push naar `main` bouwt via GitHub Actions:
- een ondertekende **APK** die als release op GitHub verschijnt (`NVictReader-1.0.<buildnummer>.apk`) — voor de
  download op nvict.nl en om direct te installeren;
- een **AAB** (app bundle) die, als de Play-secret aanwezig is, automatisch naar de **interne test**-track van
  Google Play gaat. Vanuit de Play Console promoveer je die naar productie.

Zonder keystore-secret maakt de workflow alleen een onondertekende testbuild (als artifact).

### GitHub-secrets (Settings > Secrets and variables > Actions)
| Secret | Inhoud |
|---|---|
| `KEYSTORE_BASE64` | De release-keystore `nvict-reader.jks`, base64-gecodeerd |
| `KEYSTORE_PASSWORD` | Wachtwoord van de keystore |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON-sleutel van het Google Cloud-serviceaccount voor de Play Console (zie hieronder) |

Bewaar `nvict-reader.jks` en het wachtwoord veilig (NAS). Dit is tegelijk de **upload-key** voor Google Play:
zonder deze keystore kun je geen nieuwe versies uploaden en geen updates over de geïnstalleerde app heen zetten.

## Google Play: eenmalige stappen
1. **Developer-account** op https://play.google.com/console — kies **Organisatie** (NVict Service). Nodig: D-U-N-S-nummer
   (gratis via Dun & Bradstreet, reken op ~1 week), KvK-gegevens, zakelijk e-mailadres en telefoonnummer, website
   nvict.nl. Eenmalig $25. Organisatie-accounts hoeven geen gesloten test met 12 testers te draaien.
2. **App aanmaken** in de Play Console: naam `NVict Reader`, app/gratis, standaardtaal Nederlands.
   Vul de winkelvermelding in (korte en lange beschrijving, icoon 512×512, feature graphic 1024×500,
   minimaal 2 schermafbeeldingen), de content rating-vragenlijst, de doelgroep (niet voor kinderen) en de
   **Data safety**-sectie (verzamelt geen gegevens, deelt geen gegevens).
3. **Privacybeleid**: zet een pagina op nvict.nl, bijvoorbeeld `https://www.nvict.nl/privacy-nvict-reader`, met de tekst
   dat de app geen persoonsgegevens verzamelt of verzendt en PDF's alleen lokaal verwerkt. Vul die URL in bij
   *App-inhoud > Privacybeleid*.
4. **Play App Signing** staat standaard aan: Google bewaart de app-signing-key, jij uploadt met `nvict-reader.jks`
   (de upload-key). Bij de eerste upload wordt die automatisch als upload-key geregistreerd.
5. **Eerste upload** doe je handmatig: download de `.aab` uit de GitHub-release-artifacts (of bouw lokaal) en upload
   die bij *Testen > Interne test > Nieuwe release*. Daarna werkt de automatische upload.
6. **Serviceaccount voor de automatische upload**:
   - Google Cloud Console (https://console.cloud.google.com) > maak een project `NVict Play` > *IAM en beheer >
     Serviceaccounts > Serviceaccount maken* (naam `github-actions`). Maak een **JSON-sleutel** en download die.
   - Play Console > *Gebruikers en machtigingen > Gebruiker uitnodigen* > het e-mailadres van het serviceaccount.
     Geef het bij app-machtigingen voor NVict Reader de rechten *Releases beheren* (uploaden naar test-tracks is genoeg).
   - Zet de inhoud van het JSON-bestand als secret `PLAY_SERVICE_ACCOUNT_JSON` in GitHub.
7. Elke volgende push naar `main` staat na ~5 minuten in de interne test. In de Play Console promoveer je een release
   naar **Productie** (de eerste keer duurt de review enkele dagen, daarna meestal uren).

Release notes voor Play staan in `distribution/whatsnew/whatsnew-nl-NL`; pas die aan vóór een push.

## Technisch
- Pakketnaam `nl.nvict.reader`, minimaal Android 8.0 (API 26), target API 36.
- PDF-engine: `io.legere:pdfiumandroid` (Pdfium-binding, Apache 2.0). Tekst, links en zoeken komen rechtstreeks
  uit Pdfium; pagina's worden als bitmap gerenderd en in een LRU-cache bewaard.
- Bewerkingen worden met PdfBox-Android (Apache 2.0) geschreven: markeringen als echte PDF-highlight-annotaties,
  tekst en handtekeningen in de pagina-inhoud. Pdfium rekent schermposities om naar PDF-coördinaten.
- Pagina's exporteren gebeurt met Pdfium zelf (kopie openen, andere pagina's verwijderen, opslaan).
- Afdrukken geeft het originele PDF-bestand door aan het Android-printsysteem.
- Toolchain: Gradle 9.6.1, Android Gradle Plugin 9.3.1 (ingebouwde Kotlin), Kotlin 2.4.10, Compose BOM 2026.06.01, JDK 21.

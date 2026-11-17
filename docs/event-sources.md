# Event sources: what may go on wildro, and how

Every event on wildro comes from somebody else: a national park, a club, a
group chat. This document decides, for one piece of information from one
source, whether it may be published, in what form, and what to do when the
answer is "not yet". Owner's call, 2026-10-22.

**This is not legal advice.** It is the house rule, written from German and EU
law as far as we checked it (sources at the end), deliberately on the careful
side. When a case does not fit, or someone complains formally, ask a lawyer
before arguing.

How an accepted event is written down is `docs/event-import.md`. This file
comes first: it decides whether there is anything to write down.

## The short version

1. **Facts are free, expression is not.** Date, time, meeting point, route,
   distance, price and who runs it may be taken from any source we can read
   lawfully. The wording, the photos and the logo belong to someone.
2. **Write it in our words, show our pictures.** Never copy a description,
   never take a photo from the source. Covers are freely licensed photos of the
   place, with their credit (`docs/event-import.md`, Covers).
3. **Organisations by name, people never.** "NaturFreunde Aachen" yes. A private
   person's name, phone, e-mail or profile no, unless they agreed.
4. **Closed spaces need a yes.** Anything from a WhatsApp group, a private
   Facebook group, a `t.me/c/...` chat or a personal message waits for the
   admin's or organiser's consent. So does every invite link to a group.
5. **Remove on request, at once.** If an organiser or admin asks, the event
   goes, no discussion.

## What each piece of information is

| Piece | May we use it? | Why | How |
|---|---|---|---|
| Date, time, place, meeting point, route, distance, ascent, price, difficulty as stated | Yes, from any lawfully readable source | Facts are not works under copyright | As data fields; never guess what the source does not say |
| Title | Yes, rephrased or factual | A short descriptive title is rarely protected, but copying wording is a habit to avoid | Write our own title |
| Description | **Never copied**, only rewritten | A description is a text work (§ 2 UrhG) | Our own words, all three languages, no sentences lifted |
| Photo, video, poster | **No**, not from the source | Even a snapshot is protected (§ 72 UrhG, Lichtbild); people in it have their own right (§ 22 KUG) | Free photo of the place from Wikimedia Commons with credit, the owner's own photos, or written permission |
| Logo | No | Copyright in the design; implying endorsement | Not shown; the organiser's name in text |
| Organiser that is an organisation (park, club, association, company, named public group) | Yes | Not personal data, or the public role of a legal entity | `organizer`: its name |
| Organiser that is a private person | Only with consent | Personal data (Art. 4 DSGVO); "legitimate interest" is weak for a private person on a commercial site | Name the group instead; without a group, ask |
| Phone, e-mail, private profile of a person | **No** | Personal data with no need for it here | Send people to the source link to register |
| Link to a public web page or public post | Yes | Linking freely accessible content is not a new communication to the public (CJEU Svensson, C-466/12) | `source.url`, or a link in `links` |
| Link to content behind a login, a paywall or a member area | No | It hands a closed audience's content to everyone, which the case law treats differently | Do not link; ask the owner for a public page |
| Link to something that itself infringes | No | Linking for profit to infringing content presumes knowledge (CJEU GS Media, C-160/15) | Do not link |
| Invite link to a WhatsApp, Telegram or Signal group | Only with the admin's consent | It opens a closed space to the public; members did not join a public room | After a yes, in `links.community` or `links.event` |

**Collecting at scale is a separate question.** Taking one event from a
calendar is fine. Copying a whole calendar, or most of it, again and again,
can infringe the maker's database right (§ 87b UrhG, which counts repeated
systematic taking of small parts like taking a large part). For any source we
would take more than a handful of events from regularly, ask the owner first,
or agree a feed. Automated collection from Facebook or Instagram is excluded by
Meta's terms without written consent: we do it by hand, one event at a time.

## Source classes

Classify the source first. Use the most restrictive class that applies: a
public post that only links into a WhatsApp group is class B for its facts and
class C for the group link.

| Class | Examples | Verdict | What to do |
|---|---|---|---|
| **A. Official public** | National park, tourism boards, municipalities, a club's or association's own website (DAV, NaturFreunde, Eifelverein), public event pages of organisations | **Publish** | Facts and a link to the page. Members-only offers say so in the text and point to the source to register |
| **B. Public social** | A public Facebook group or page (posts visible without login), a public Facebook event, a public Telegram channel or group with a `t.me/name` address and web preview, public Instagram | **Publish with limits** | Facts and a link to the post. Organiser: the group or page name, never the person who posted. No images, no copied text, by hand only. The group itself as a community link is fine for pages and channels; for groups of private people ask first |
| **C. Semi-private** | WhatsApp groups (invite links), private Facebook groups, Telegram chats with `t.me/c/...`, groups without web preview, Discord or Signal servers | **Ask first** | Nothing goes up before the admin agrees, facts included: members shared it inside, not with the web. Send the request (templates below), record the answer |
| **D. Personal** | A message forwarded to us, a flyer photo, "my friend organises..." | **Ask the organiser** | Same as C, addressed to the organiser |
| **E. Closed** | Paywalled, members-only pages, anything requiring an account we would not otherwise have | **Do not publish** | Ask the owner for a public page or a feed |

Signs of a class C source: the address contains `chat.whatsapp.com`, `t.me/c/`
or `t.me/+`; the page shows nothing without logging in; the group calls itself
private ("Privat", "Private group"); the content was visible only because
someone is a member.

## Conditions on every event, whatever the class

- **The event is open to the public**, or the text says who may join (members
  only, registration needed, limited places). Never invite people to something
  they cannot attend.
- **Every fact is from the source.** What the source does not say is left out
  or marked approximate with who gives the exact detail (`docs/event-import.md`,
  Meeting point not exact).
- **`source.url` points to where it was announced** and `source.readAt` is when
  we read it. The page carries "data from the source, check there" by design.
- **Our own words, our own pictures**, as above.
- **Removal on request within one working day**, from any organiser, admin or
  person named. The imprint and the privacy policy already promise it.

## Asking for consent

Who to ask: the group's admin (class C), the organiser (class D), the owner of
the calendar (bulk). Ask for exactly what will be shown, so the yes covers it.

What a request names:

1. What wildro is: a free, ad-funded directory of outdoor events around
   Aachen, run by Nikita Moritz, in German, English and Russian.
2. What exactly would appear: date, time, meeting point, a short description in
   our words, the organiser's name as they wish it, the link to the group or not.
3. What would not appear: photos from the group, members' names or numbers.
4. That it is free, they can say no, and anything is removed on request.
5. Optionally: whether they would like to send events themselves from now on.

Record every answer in a consent log kept outside the repository (it holds
names of admins): date, source, who answered,
what they agreed to (facts, group link, organiser name), and where the answer
lives (chat, e-mail). A no is recorded too, so nobody asks twice.

### Request, Russian

> Здравствуйте! Меня зовут Никита, я делаю wildro.app: бесплатный каталог
> походов и outdoor-событий вокруг Ахена на немецком, английском и русском.
> Можно ли разместить у нас ваше событие «<название>» (<дата>)? Мы покажем дату,
> время, место сбора и короткое описание своими словами, организатором укажем
> «<название группы>» и, если вы не против, ссылку на группу. Фото из группы,
> имена и телефоны участников мы не публикуем. Это бесплатно, и любое событие
> уберём по первой просьбе. Если удобно, присылайте события и дальше, будем рады.

### Request, German

> Hallo! Ich bin Nikita und betreibe wildro.app, ein kostenloses Verzeichnis von
> Wanderungen und Outdoor-Events rund um Aachen, auf Deutsch, Englisch und
> Russisch. Dürfen wir euer Event „<Titel>“ am <Datum> bei uns zeigen? Wir zeigen
> Datum, Uhrzeit, Treffpunkt und eine kurze Beschreibung in eigenen Worten, als
> Veranstalter „<Gruppenname>“ und, wenn ihr einverstanden seid, den Link zur
> Gruppe. Fotos aus der Gruppe, Namen oder Telefonnummern von Mitgliedern
> veröffentlichen wir nicht. Das ist kostenlos, und jedes Event nehmen wir auf
> Wunsch sofort wieder herunter. Wenn ihr mögt, schickt uns gern auch künftige
> Termine.

### Reply to a removal request, German

> Danke für den Hinweis, das Event ist entfernt. Entschuldigt die Umstände.

## Sources

- § 2, § 72, § 87b UrhG; § 22, § 23 KUG; Art. 4, 6, 14, 21 DSGVO.
- CJEU C-466/12 *Svensson* (links to freely accessible works), C-348/13
  *BestWater* (framing), C-160/15 *GS Media* (links to infringing content,
  presumption with a profit motive), as summarised by
  [Heuking](https://www.heuking.de/de/news-events/newsletter-fachbeitraege/artikel/eugh-keine-oeffentliche-wiedergabe-eines-werks-allein-durch-framing.html),
  [Otto Schmidt](https://www.otto-schmidt.de/news/wirtschaftsrecht/profitorientierte-links-auf-websites-mit-urheberrechtsverletzungen-sind-verboten-2016-09-09.html),
  [Verfassungsblog](https://verfassungsblog.de/hyperlinks-ein-urheberrechtliches-minenfeld/).
- [§ 87b UrhG](https://www.buzer.de/87b_UrhG.htm), database maker's right.
- [Art. 14 DSGVO](https://gesellschaft-datenschutz.de/dsgvo/art-14-dsgvo) and
  [public sources still need a legal basis](https://www.dr-datenschutz.de/die-verarbeitung-von-oeffentlich-zugaenglichen-daten/).
- Meta's Automated Data Collection Terms (no automated collection without
  written consent), reported by
  [TechCrunch](https://techcrunch.com/2022/10/03/meta-settles-lawsuit-for-significant-sum-against-businesses-scraping-facebook-and-instagram-data/);
  check the current terms before anything automated.

Checked 2026-10-22. Revisit when wildro starts collecting automatically, takes
a feed from a partner, or receives a formal complaint.

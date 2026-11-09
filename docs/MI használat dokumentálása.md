# MI használat dokumentálása

> A választott feladat a házifeladat leírásában példaként adott bevásárolólista.

---

## Követelményelemzés, specifikáció

### 1. Lépés: A követelményeknek megfelelő specifikáció elkészítése

Az információ gyűjtés, tervezés során a Gemini 3.6 Flash modellt és a ChatGPT-t használtam. A feladat leírásban megadott szempontok (felhasználókezelés, lista megosztás lehetősége) elemzését a Gemini végezte. A többi információ (platform, egyéb döntések) általam került megadásra, melyek elemzése szintén a Gemini által történt.

- Bejelentkezési lehetőségek: én adtam meg.

- Vendégfelhasználó: Először a Gemini adta meg, hogy legyen ilyen funkció, majd a ChatGPT ellenőrzése után úgy döntöttem nem lesz ilyen lehetőség.

- Google fiókkal való bejelntkezés: A Gemini először ezt a lehetőséget is felvetette, azonban a külső API-k haszálatának minimalizálása érdekében (melyre a ChatGPT hívta fel a figyelmem miután elemezte a **végleges** specifikációt) ezt a lehetőséget elvetettem.

- Gépelési hiba felhasználónév megosztásakor (specifikációban felvetett bizonytalanság): A Gemini két lehetőséget ajánlott fel: 
  
  - A megosztási mező gépelés közben automatikus kiegészítést (keresőt) biztosít a létező felhasználónevekre, vagy 
  
  - A megosztás előtt egy megerősítő ablakban kiírja a célfelhasználó pontos nevét.
  
  A végső döntést én hoztam, a második lehetőséget választottam megfelelő hibakezeléssel. További fejlesztési lehetőségként viszont a végleges specifikációban bent maradt mind a két lehetőség.

Konklúzió: A Gemini a követelmények elemzését majd egy azoknak megfelelő specifikáció írását lényegesen lerövidítette. Munkája elsőre nem volt tökéletes, javítanom kellett. A javított dokumentumot ezután a ChatGPT elemezte, ami még felfedezett ellentmondásokat. A fontosabb döntéseket én hoztam, az információ keresés közösen történt, a végleges dokumentumot pedig a Gemini szerkesztette.

---

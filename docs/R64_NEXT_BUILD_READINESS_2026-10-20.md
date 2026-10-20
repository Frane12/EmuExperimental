# DRNAS R64 Mali Blender 4.5.14 — sljedeći build (PRIPREMLJENO, NIJE POKRENUTO)
Datum: 20. 10. 2026.

## Potvrđeno iz SHA256-verificirane binarne usporedbe

- Službeni v1.1 Blender ZIP SHA256: `0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911`.
- Naš prethodni Mali R64 ZIP SHA256: `8802767f9ac63a6e8054e6e37b3ba994b49c2fb6d37d34cfc50ae48491851d6f`.
- `blender.pdb`: 1.012,45 MiB kod nas naspram 91,57 MiB kod službene distribucije.
- **190 Git LFS pointera** nalazilo se u instalacijskim resursima (assets, .blend, .exr).
- 5.065 jednakih, 204 različite datoteke; 131 samo u službenoj, 12 samo u našoj verziji.
- Izvorna krivnja crasha `c0000005` **nije utvrđena**; ovo popravlja dokazanu grešku pakiranja, ne proglašava Wine/Blender izvršavanje ispravnim.

## Što je sada pripremljeno u izoliranoj privatnoj grani

1. **Izvorni Blender Git LFS** nakon preuzimanja windows_arm64 biblioteka: `git lfs pull --include="assets/**,release/datafiles/**,scripts/**" --exclude=""`.
2. **Fail-closed source-scan** provjerava sve male datoteke u trima relevantnim stablima i četiri referentna stvarna resursa (*.blend, *.exr). Svaki Git LFS pointer zaustavlja proces prije CMake kompiliranja.
3. **Fail-closed post-install pregled**: Windows ARM64 PE `0xAA64`; svih 5.000+ instalacijskih datoteka; nema Git LFS pointera bilo gdje; četiri referentna resursa imaju pravi binarni magic.
4. **Dva odvojena privatna ZIP-a**: runtime bez svih `.pdb` datoteka; symbols ZIP s `blender.pdb` za kasnije mapiranje Wine crash adresa. Ne gubiti PDB dok se ne analizira crash.
5. **Integritet i resursni limiti**: dva SHA256 zapisa, manifest, zasebne ZIP inventure i CRC, runtime ZIP maksimalno 350 MiB, prošireni runtime maksimalno 1 GiB, oba GitHub asseta <1,9 GB, privatni draft.
6. **Sintetički no-build regresijski testovi**: LFS pointer, provjera čarobnih brojeva datoteka, ARM64 PE, PDB izolacija, hash manifest, preveliki paket.
7. **Bez automatskog workflow push triggera**: samo `workflow_dispatch` s eksplicitnim checkboxom `user_approved_build=true`. Grana pripremljena, originalna radna grana i stabilni Blender v1.1 ostaju netaknuti.

## Za build (tek nakon korisnikova odobrenja)

- Potvrditi target granu i ime budućeg izdanja te odobriti zasebni **Windows ARM64 Blender** testni build. Bilo kakav GitHub Actions full Blender `make.bat` ili Android APK build ostaje zabranjen do tog dogovora.
- Kad korisnik odobri, izvršiti pripremljeni privatni workflow za pripremljenu granu uz `user_approved_build=true`. GitHub može zahtijevati registraciju workflowa na default grani ili posebno odobren push-trigger (tek uz korisnikovo odobrenje); nikad automatski ne preslikavati ili objavljivati stabilan release.
- Uspjeh CI-ja je samo dokaz da je kompajliranje/pakiranje prošlo. Zadržati objavu isključivo u PRIVATE draftu. Očuvati sidecar PDB i zabilježiti SHA256 novog ZIP-a iz `DISTRIBUTION-MANIFEST.json`.
- **Tek zatim** zasebno pripremiti Android R64 LAB koristeći baš novu i verificiranu SHA256/asset ID/release ID; ni jedan prethodni `880276...` pin ne smije ostati. Javne stabilne APK-ove nikad ne mijenjati.
- Na telefonu A/B ispitati `blender.exe --version`, headless Python marker i GUI, uz pariranje s v1.1, pa tek potom Malt GPU/Vulkan scene QA.

## Neriješene sigurnosne i funkcionalne provjere

- PDB i LFS problemi jesu dokazani, ali ne dokazuju da patch neće srušiti Blender. Usporediti `.text/.rdata`, import tablicu i adresu Wine `c0000005` s odgovarajućim PDB-om.
- Mali Vulkan patch zasad dopušta nepostojeće `multiViewport`, `shaderClipDistance`, `dualSrcBlend` funkcije: dio zahtjeva je uklonjen prije pune zamjenske semantike, potrebno je QA. Osobito je `VK_EXT_provoking_vertex` default FIRST_VERTEX drukčiji od originalnog LAST_VERTEX.
- Ispitati razliku 131 datoteke samo u službenoj distribuciji prije javnog releasea; nije dokazano da je svaka obavezna.
- Provjeriti GPL uvjete redistribucije i corresponding source, te licencne obavijesti prije bilo kakve javne objave.

### Napomena

`prepare_r64_blender_distribution.py` **ne kompajlira** Blender ni APK, samo validira gotovi build i priprema ZIP. Izvorni Vulkan C++ patch nije mijenjan. Ovo je isključivo pre-build priprema.

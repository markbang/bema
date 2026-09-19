# Changelog

## [0.0.8](https://github.com/markbang/bema/compare/v0.0.7...v0.0.8) (2026-09-19)


### Features

* **accounts:** show the instance's Memos version in the account actions ([509ea12](https://github.com/markbang/bema/commit/509ea128c21fcd9e1fcd864d94ec348e3e67d282))
* **activity:** filter the timeline to a day tapped in the panel ([b224ac4](https://github.com/markbang/bema/commit/b224ac4b2ec2e9c3dd8fc6760c2347e6c7ae6dcc))
* **activity:** read the instance's user stats ([bca5138](https://github.com/markbang/bema/commit/bca51381af6fdb619723b01b8d4410a43d8443d8))
* **activity:** reveal an activity panel by dragging the timeline sideways ([7b5660c](https://github.com/markbang/bema/commit/7b5660caa0a76c16c8b9b9e73403fb81efce85e4))
* **ios:** reveal the activity panel by dragging the timeline sideways ([b83ccc1](https://github.com/markbang/bema/commit/b83ccc1a3fbb956b7fd616b6827a95493e248088))
* **ios:** show the instance's Memos version in the account actions ([49fdb13](https://github.com/markbang/bema/commit/49fdb1351588711a0a50a6ae561743de5696c7ee))


### Bug Fixes

* **activity:** keep the panel above the bottom bar ([8c02d1c](https://github.com/markbang/bema/commit/8c02d1cbc5f826103a77a7904b1c06004b01b5e5))
* **ios:** box the tapped day for the Kotlin Long parameter ([86b0dd0](https://github.com/markbang/bema/commit/86b0dd00fb66b1d5c648f7ec54e80327852babdc))
* **ios:** import the Foundation package so NSTimeZone's class properties resolve ([ca3b361](https://github.com/markbang/bema/commit/ca3b361b9aac734ee1a069ba2dd80aaa6a5946c5))
* **ios:** rename the chip's tag property, which collided with UIView.tag ([5ad34d4](https://github.com/markbang/bema/commit/5ad34d417527992a10b9a4ac81196564add8edd1))

## [0.0.7](https://github.com/markbang/bema/compare/v0.0.6...v0.0.7) (2026-09-19)


### Features

* **update:** download the APK in the app instead of sending users to a browser ([9812ad8](https://github.com/markbang/bema/commit/9812ad809396286be429bf7173e72cedc0f47bab))


### Bug Fixes

* reach the auth endpoints over Connect so the session survives a restart ([8bcb59b](https://github.com/markbang/bema/commit/8bcb59bbde8f17417d8c29c5569e45b261ed2162))
* send the reaction name in the body and refresh the token before it lapses ([815d654](https://github.com/markbang/bema/commit/815d65423214e8c1ad0d53e54f368365495e7145))
* **settings:** make the settings sheet scroll ([063b992](https://github.com/markbang/bema/commit/063b992346c8461b5bd0d49339db4c8a6b049de1))
* **update:** make the download's Cancel actually cancel ([1365c0d](https://github.com/markbang/bema/commit/1365c0d192bf8241c6cc2242a32ba2834ab2bff0))

## [0.0.6](https://github.com/markbang/bema/compare/v0.0.5...v0.0.6) (2026-09-18)


### Bug Fixes

* keep the optimistic reaction placeholder off the wire ([76c37ea](https://github.com/markbang/bema/commit/76c37ea53d379362e006b908f23e8b00eefac63a))
* send the bodies the server actually binds for settings and attachments ([837d1ff](https://github.com/markbang/bema/commit/837d1ff83f304cc2bde42331a1c43f7ec960146f))

## [0.0.5](https://github.com/markbang/bema/compare/v0.0.4...v0.0.5) (2026-09-18)


### Features

* **release:** name release APKs bema-v&lt;version&gt;-android-&lt;abi&gt;.apk ([5dd7cfc](https://github.com/markbang/bema/commit/5dd7cfc3308949d8949771e2485a61b373e75094))
* **update:** use the app-scoped catalog on the update host ([e6fdcd4](https://github.com/markbang/bema/commit/e6fdcd4c9689deea78585fcc7792492f7e35251f))


### Bug Fixes

* **ios:** read the initial theme through a typed accessor ([2d1b037](https://github.com/markbang/bema/commit/2d1b03779dfb3d5ce3937796b437b5f54cec165f))
* repair the memo link and the comment request body ([459c3ce](https://github.com/markbang/bema/commit/459c3ced80b11eeb019a335588e0c76469428ea3))
* send the memo name when upserting a reaction ([4de461e](https://github.com/markbang/bema/commit/4de461e48a5954038874999b304e313c7f7b6f8a))

## [0.0.4](https://github.com/markbang/bema/compare/v0.0.3...v0.0.4) (2026-09-18)


### Features

* **android:** collapse the timeline chrome and pick settings ([0fc63b7](https://github.com/markbang/bema/commit/0fc63b74bcda652fa42443248b52c4ddc67a9499))
* **android:** wire post actions, likes, and an image viewer ([0663637](https://github.com/markbang/bema/commit/0663637a9f9774136579bb92714b9908582007b7))
* **ios:** rebuild the UI on SharedLogic ([ebc53b5](https://github.com/markbang/bema/commit/ebc53b51a0ed4110e024439b4deca68b0260f159))
* **ios:** render the miuix icons as vectors ([bb2f38c](https://github.com/markbang/bema/commit/bb2f38cb8c60e074a3dc4c192dd8f631df4209b1))
* **shared:** make the suspend API and byte buffers usable from Swift ([d831a70](https://github.com/markbang/bema/commit/d831a70516a94b1faa893a816916ef7d901a0e18))
* **shared:** share like state, timestamps and settings options ([72b7692](https://github.com/markbang/bema/commit/72b76929d14c2b764b077549b8b0d904ee53b59b))
* **theme:** add a light/dark preference shared by both apps ([6411117](https://github.com/markbang/bema/commit/6411117dd0ba482efbb0294f5245bf22bb9e590e))
* **update:** prompt for a newer APK from the release catalog ([28ad521](https://github.com/markbang/bema/commit/28ad52166078531de1dbf42c991a8e47418c43f6))


### Bug Fixes

* **ios:** import the framework in the app delegate and fix two initializers ([80c12a6](https://github.com/markbang/bema/commit/80c12a6d66d1974152777cb6dbaa623f08fadc92))
* **ios:** import the shared framework and give the custom views an init ([3e71e67](https://github.com/markbang/bema/commit/3e71e677f220afd96e1dda95baa95a01150c0b38))
* **ios:** keep shared storage encrypted ([b640387](https://github.com/markbang/bema/commit/b6403878c793bcd3a1b3657e8b57dfafcf7a0d39))
* **ios:** pass the control event when removing the newer-memos action ([6a94217](https://github.com/markbang/bema/commit/6a94217a0285df2c5cb848714577b8b28aad5e48))
* **ios:** resolve the remaining compile errors ([6ac95a5](https://github.com/markbang/bema/commit/6ac95a534e2d8e607e929337246271601d2c216d))
* **ios:** unwrap self before the async call and use the action-based bar item ([aba4aae](https://github.com/markbang/bema/commit/aba4aaef771d121274460ffe244b3bddc9ad3c71))
* **ios:** use the generated name for the visibility enum entries ([591ed8d](https://github.com/markbang/bema/commit/591ed8d85115759bde2bfc5fa89e9cdd85ea0608))
* **shared:** opt in to the foreign API in the iOS key-value store ([0484624](https://github.com/markbang/bema/commit/0484624e06b2b9e07c772580dd079110ebf6ef4a))

## [0.0.3](https://github.com/markbang/bema/compare/v0.0.2...v0.0.3) (2026-09-17)


### Features

* **android:** add search, instance settings, caching, and predictive back ([b2d84f2](https://github.com/markbang/bema/commit/b2d84f29ab43277cb07be2167b766bd1a1ce6434))
* **android:** rework account switcher into an actions bottom sheet ([e78f47e](https://github.com/markbang/bema/commit/e78f47ea6af38af10e3ec5c923c42df58b5b2a1a))


### Bug Fixes

* **android:** keep account sheet above the gesture bar ([aefdfb1](https://github.com/markbang/bema/commit/aefdfb1d1b164b18ba21a53d09205599644211ca))

## [0.0.2](https://github.com/markbang/bema/compare/v0.0.1...v0.0.2) (2026-09-17)


### Features

* **android:** add real markdown editing and long-press account switch ([2758096](https://github.com/markbang/bema/commit/275809693e33a3fc94cc192f30e0f1f73b6398ab))
* **android:** adopt miuix component library ([6989560](https://github.com/markbang/bema/commit/6989560dd89a0cc9fbc0a97fd27951392f97b7d1))
* **android:** expand miuix UI and icons ([a367e06](https://github.com/markbang/bema/commit/a367e067de80ebdd90c433799d8d35d992dae8ec))
* **preview:** add emulator UI catalog workflow ([d2cc5d9](https://github.com/markbang/bema/commit/d2cc5d9897d65dd37a10c53d9b163844f50ca9e1))
* **ui:** preview photos while composing ([7565979](https://github.com/markbang/bema/commit/75659798748e29b04ad2bebd4e17523cdca4d4d2))
* **ui:** redesign timeline for social browsing ([17100a2](https://github.com/markbang/bema/commit/17100a229b29e2483545717b80ab39dd7312cd41))
* **ui:** simplify navigation and add markdown editor ([5e61f5d](https://github.com/markbang/bema/commit/5e61f5d2ff203bd87824a523609c705de3a97698))


### Bug Fixes

* **android:** avoid double bottom navigation inset ([0cb4bf8](https://github.com/markbang/bema/commit/0cb4bf8bd4061ecc863a5ae7b9f75fdbd1550949))
* **android:** avoid duplicate detail floating actions ([d887025](https://github.com/markbang/bema/commit/d8870250ceafd03109af78565c905330b9b8e429))
* **android:** respect system bar insets ([3436935](https://github.com/markbang/bema/commit/343693575d605702d9fb853d55078051c7757847))
* **ci:** scan Kotlin without build instrumentation ([a0ccb39](https://github.com/markbang/bema/commit/a0ccb3900b2433e05d4db0e1aebba622b9bd223c))
* **ios:** expose instance branding models ([64a4493](https://github.com/markbang/bema/commit/64a4493e71c0c2d66c9caa63649dfe853d7491e6))
* **preview:** discover instrumentation screenshot path ([317f43d](https://github.com/markbang/bema/commit/317f43d6286e038b6326914de7dc45662175b241))
* **preview:** export screenshots from emulator downloads ([19a69e2](https://github.com/markbang/bema/commit/19a69e204d6c5a6578e8b820b322ae2a8d18b24a))
* **release:** publish signed ABI-specific APKs ([0dbd8ce](https://github.com/markbang/bema/commit/0dbd8ce1b8091e74afc348d15adac5e2c42d8916))
* **test:** remove unused imports and simplify test flow ([53818c6](https://github.com/markbang/bema/commit/53818c662a56d6acc8f21c7150aa0ae7a8426ade))
* **test:** use floating action button for composer ([8e307d1](https://github.com/markbang/bema/commit/8e307d1b9cecda84c7a279dd6970659ed4b19f04))

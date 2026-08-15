# MIT License

Copyright (c) 2026 BeestoXd

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## Third-party notices

UltimateVirtualSpawner is built against, but does not bundle, the following:

| Dependency | Scope | License |
|---|---|---|
| Spigot / Paper API | `provided` | GNU GPL v3 (Spigot API), MIT (Paper API) |
| VaultAPI | `provided` | LGPL v3 |
| PlaceholderAPI | `provided` | GNU GPL v3 |
| SQLite JDBC (`org.xerial`) | runtime, downloaded by the server | Apache-2.0 |
| MySQL Connector/J | runtime, downloaded by the server | GPL v2 with FOSS Exception |
| JUnit Jupiter | `test` | EPL-2.0 |

The JDBC drivers are declared as `libraries` in `plugin.yml` and fetched by the
server at start-up, so they are never shipped inside the plugin jar. Minecraft
itself and its assets are property of Mojang Studios and are not covered by
this license.

# App icon

Approved concept N01: Anton “CL” lettering in pale yellow `#fff29c`,
nav green `#60b048`, and a dark-green `#407c35` left edge.

`app-icon.svg` is the full-bleed master, shared with iOS. Lettering is
outlined, so no font is loaded at runtime.

Android uses 108 dp vector layers. The 512-unit design maps into the central
72 dp mask area (18 dp inset), keeping the lettering inside the adaptive-icon
safe zone. The background extends to the full layer. The monochrome layer
contains only the CL silhouette so themed launchers can apply their own colors.

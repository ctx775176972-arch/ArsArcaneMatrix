Add-Type -AssemblyName System.Drawing

$output = Join-Path $PSScriptRoot "..\src\main\resources\assets\ars_arcane_matrix\textures\item\matrix_construction_wand.png"
$bitmap = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Set-Pixel([int]$x, [int]$y, [string]$hex) {
    $bitmap.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($hex))
}

# A continuous three-pixel diagonal wooden shaft, following the readable silhouette
# of a classic wand while using an original warm sourcewood palette.
for ($y = 7; $y -le 14; $y++) {
    $center = 15 - $y
    Set-Pixel ($center - 1) $y "#2A1720"
    Set-Pixel $center $y "#C18442"
    Set-Pixel ($center + 1) $y "#57301F"
}
Set-Pixel 1 14 "#3A2020"
Set-Pixel 2 13 "#E0A75B"
Set-Pixel 3 12 "#E0A75B"
Set-Pixel 4 11 "#E0A75B"
Set-Pixel 5 10 "#E0A75B"
Set-Pixel 6 9 "#E0A75B"
Set-Pixel 7 8 "#E0A75B"

# Large faceted Source Gem head. The dark outline keeps it legible over JEI and
# inventory backgrounds; the pale pixels form the Ars-like magical highlight.
$outline = @(
    @(11,1), @(12,1), @(13,1),
    @(9,2), @(10,2), @(14,2),
    @(8,3), @(15,3),
    @(8,4), @(15,4),
    @(8,5), @(15,5),
    @(9,6), @(14,6),
    @(10,7), @(11,7), @(12,7), @(13,7)
)
foreach ($p in $outline) { Set-Pixel $p[0] $p[1] "#25113D" }

$dark = @(
    @(11,2), @(12,2), @(13,2),
    @(9,3), @(10,3), @(14,3),
    @(9,4), @(14,4),
    @(9,5), @(10,5), @(14,5),
    @(10,6), @(13,6)
)
foreach ($p in $dark) { Set-Pixel $p[0] $p[1] "#5B1A91" }

$mid = @(
    @(11,3), @(12,3), @(13,3),
    @(10,4), @(11,4), @(12,4), @(13,4),
    @(11,5), @(12,5), @(13,5),
    @(11,6), @(12,6)
)
foreach ($p in $mid) { Set-Pixel $p[0] $p[1] "#A23BE0" }

Set-Pixel 10 3 "#D977FF"
Set-Pixel 10 4 "#E6A0FF"
Set-Pixel 11 4 "#F4D9FF"
Set-Pixel 11 5 "#CB67F1"
Set-Pixel 13 5 "#7122AD"
Set-Pixel 13 6 "#42146D"

$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()

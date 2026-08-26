Add-Type -AssemblyName System.Drawing

$textureDir = Join-Path $PSScriptRoot "..\src\main\resources\assets\ars_arcane_matrix\textures\block"
$referenceDir = Join-Path $PSScriptRoot "..\build\texture_reference\ars\assets\ars_nouveau\textures\block"
$oldFront = Join-Path $textureDir "arcane_smelter_front.png"
$oldLit = Join-Path $textureDir "arcane_smelter_front_on.png"
$furnaceFront = Join-Path $textureDir "source_stone_furnace_front.png"
$furnaceLit = Join-Path $textureDir "source_stone_furnace_front_on.png"
$sourceStone = Join-Path $referenceDir "sourcestone.png"

# Preserve the former shared face as the simple single-block furnace artwork.
if (-not (Test-Path $furnaceFront)) { [System.IO.File]::Copy($oldFront, $furnaceFront, $true) }
if (-not (Test-Path $furnaceLit)) { [System.IO.File]::Copy($oldLit, $furnaceLit, $true) }

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Set-Pixel($bitmap, [int]$x, [int]$y, [string]$hex) {
    $bitmap.SetPixel($x, $y, (Color $hex))
}

function Fill-Rect($bitmap, [int]$x1, [int]$y1, [int]$x2, [int]$y2, [string]$hex) {
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) { Set-Pixel $bitmap $x $y $hex }
    }
}

function New-BaseTexture {
    $source = [System.Drawing.Bitmap]::FromFile($sourceStone)
    $result = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($result)
    $graphics.DrawImage($source, 0, 0, 16, 16)
    $graphics.Dispose()
    $source.Dispose()
    return $result
}

function Draw-Frame($bitmap) {
    # Heavy casting-frame silhouette and recessed furnace door.
    Fill-Rect $bitmap 1 1 14 2 "#251D31"
    Fill-Rect $bitmap 1 13 14 14 "#211A2B"
    Fill-Rect $bitmap 1 3 3 12 "#30243D"
    Fill-Rect $bitmap 12 3 14 12 "#30243D"
    Fill-Rect $bitmap 4 3 11 3 "#46344F"
    Fill-Rect $bitmap 4 12 11 12 "#17131F"
    Fill-Rect $bitmap 4 4 11 11 "#15111C"
    Fill-Rect $bitmap 5 5 10 10 "#26162A"

    # Violet enchanted-metal bands.
    Fill-Rect $bitmap 2 4 2 11 "#684079"
    Fill-Rect $bitmap 13 4 13 11 "#684079"
    Fill-Rect $bitmap 4 2 11 2 "#765084"
    Set-Pixel $bitmap 2 2 "#C58B49"
    Set-Pixel $bitmap 13 2 "#C58B49"
    Set-Pixel $bitmap 2 13 "#8D5A34"
    Set-Pixel $bitmap 13 13 "#8D5A34"
}

function Draw-Front([bool]$lit, [string]$output) {
    $bitmap = New-BaseTexture
    Draw-Frame $bitmap
    if ($lit) {
        Fill-Rect $bitmap 5 6 10 10 "#8C2818"
        Fill-Rect $bitmap 6 7 9 10 "#E65A19"
        Fill-Rect $bitmap 6 8 9 9 "#FFB52E"
        Set-Pixel $bitmap 7 8 "#FFF2A1"
        Set-Pixel $bitmap 8 7 "#FFD45B"
        Set-Pixel $bitmap 5 5 "#7E2D60"
        Set-Pixel $bitmap 10 5 "#7E2D60"
    } else {
        Fill-Rect $bitmap 5 6 10 10 "#211521"
        Fill-Rect $bitmap 6 8 9 9 "#5C241C"
        Set-Pixel $bitmap 7 8 "#A34523"
        Set-Pixel $bitmap 8 8 "#7C2C24"
    }
    # Casting-crystal chevron identifies the multiblock core.
    Set-Pixel $bitmap 6 4 "#B45B38"
    Set-Pixel $bitmap 7 5 "#E08B42"
    Set-Pixel $bitmap 8 5 "#E08B42"
    Set-Pixel $bitmap 9 4 "#B45B38"
    $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Draw-Side([string]$output) {
    $bitmap = New-BaseTexture
    Fill-Rect $bitmap 2 1 3 14 "#30243D"
    Fill-Rect $bitmap 12 1 13 14 "#30243D"
    Fill-Rect $bitmap 3 3 12 4 "#4A3654"
    Fill-Rect $bitmap 3 11 12 12 "#241C2E"
    Set-Pixel $bitmap 2 3 "#C58B49"
    Set-Pixel $bitmap 13 3 "#C58B49"
    Set-Pixel $bitmap 2 12 "#8D5A34"
    Set-Pixel $bitmap 13 12 "#8D5A34"
    # Small molten-crystal rune on both side panels.
    Set-Pixel $bitmap 7 6 "#7B315C"
    Set-Pixel $bitmap 8 6 "#7B315C"
    Set-Pixel $bitmap 6 7 "#A84B45"
    Set-Pixel $bitmap 9 7 "#A84B45"
    Set-Pixel $bitmap 7 8 "#E08B42"
    Set-Pixel $bitmap 8 8 "#E08B42"
    Set-Pixel $bitmap 7 9 "#7B315C"
    Set-Pixel $bitmap 8 9 "#7B315C"
    $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

Draw-Front $false (Join-Path $textureDir "arcane_smelter_front.png")
Draw-Front $true (Join-Path $textureDir "arcane_smelter_front_on.png")
Draw-Side (Join-Path $textureDir "arcane_smelter_side.png")

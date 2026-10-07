Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot ".."
$dustPath = Join-Path $root "src\main\resources\assets\ars_arcane_matrix\textures\item\iron_dust.png"
$outputPath = Join-Path $root "src\main\resources\assets\ars_arcane_matrix\textures\block\arcane_crusher_item_front.png"

$dust = [System.Drawing.Bitmap]::FromFile($dustPath)
$result = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($result)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#44394D'))
$graphics.DrawImage($dust, 0, 0, 16, 16)
$graphics.Dispose()
$dust.Dispose()
$result.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$result.Dispose()

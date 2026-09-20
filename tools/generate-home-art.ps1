# Exports a compact Home illustration from the repository-owned Milestone 19C robot sheets.
# The battle atlas remains untouched; the generated PNG contains no text or branding.
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$sourceDirectory = Join-Path $PSScriptRoot '../docs/art-source'
$outputDirectory = Join-Path $PSScriptRoot '../android/app/src/main/res/drawable-nodpi'
$outputFile = Join-Path $outputDirectory 'home_robot_duel.png'
$blueLobbyFile = Join-Path $outputDirectory 'lobby_robot_blue.png'
$redLobbyFile = Join-Path $outputDirectory 'lobby_robot_red.png'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

function Export-LobbyRobot($sourceImage, $destination) {
    $bitmap = New-Object Drawing.Bitmap 256, 256, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $drawing = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $drawing.Clear([Drawing.Color]::Transparent)
        $drawing.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceOver
        $drawing.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
        $drawing.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $drawing.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
        $drawing.DrawImage($sourceImage, [Drawing.Rectangle]::new(0, 0, 256, 256),
            0, 0, 256, 256, [Drawing.GraphicsUnit]::Pixel)
        $bitmap.Save($destination, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $drawing.Dispose()
        $bitmap.Dispose()
    }
}

$blue = [Drawing.Image]::FromFile((Join-Path $sourceDirectory 'blue-robot-sheet-source.png'))
$red = [Drawing.Image]::FromFile((Join-Path $sourceDirectory 'red-robot-sheet-source.png'))
$canvas = New-Object Drawing.Bitmap 800, 280, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($canvas)
try {
    $graphics.Clear([Drawing.Color]::Transparent)
    $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $sourceCell = [Drawing.Rectangle]::new(0, 0, 256, 256)
    $graphics.DrawImage($blue, [Drawing.Rectangle]::new(35, 8, 264, 264),
        $sourceCell.X, $sourceCell.Y, $sourceCell.Width, $sourceCell.Height, [Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawImage($red, [Drawing.Rectangle]::new(501, 8, 264, 264),
        $sourceCell.X, $sourceCell.Y, $sourceCell.Width, $sourceCell.Height, [Drawing.GraphicsUnit]::Pixel)

    $cyanGlow = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(80, 66, 215, 255)), 12
    $orangeGlow = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(80, 255, 147, 77)), 12
    $cyanLine = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(210, 66, 215, 255)), 4
    $orangeLine = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(210, 255, 147, 77)), 4
    try {
        $graphics.DrawArc($cyanGlow, 331, 71, 138, 138, 95, 170)
        $graphics.DrawArc($orangeGlow, 331, 71, 138, 138, 275, 170)
        $graphics.DrawArc($cyanLine, 344, 84, 112, 112, 95, 170)
        $graphics.DrawArc($orangeLine, 344, 84, 112, 112, 275, 170)
        $graphics.FillEllipse((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(220, 238, 251, 255))), 387, 127, 26, 26)
    } finally {
        $cyanGlow.Dispose()
        $orangeGlow.Dispose()
        $cyanLine.Dispose()
        $orangeLine.Dispose()
    }

    $canvas.Save($outputFile, [Drawing.Imaging.ImageFormat]::Png)
    Export-LobbyRobot $blue $blueLobbyFile
    Export-LobbyRobot $red $redLobbyFile
} finally {
    $graphics.Dispose()
    $canvas.Dispose()
    $blue.Dispose()
    $red.Dispose()
}

foreach ($generatedFile in @($outputFile, $blueLobbyFile, $redLobbyFile)) {
    $image = [Drawing.Image]::FromFile($generatedFile)
    try {
        Write-Output "Generated $generatedFile ($($image.Width) x $($image.Height))"
    } finally {
        $image.Dispose()
    }
}

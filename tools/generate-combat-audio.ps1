# Deterministic, original synthesized arcade effects. No samples or external tools are used.
$ErrorActionPreference = 'Stop'
$sampleRate = 22050
$destination = Join-Path $PSScriptRoot '../android/app/src/main/res/raw'

function Envelope([double]$time, [double]$duration, [double]$attack = 0.012, [double]$release = 0.08) {
    $rise = [math]::Min(1.0, $time / $attack)
    $fall = [math]::Min(1.0, ($duration - $time) / $release)
    return [math]::Max(0.0, [math]::Min($rise, $fall))
}

function Write-Effect($name, [double]$duration, [scriptblock]$generator) {
    $count = [int]($duration * $sampleRate)
    $samples = New-Object 'System.Collections.Generic.List[double]' $count
    $peak = 0.0001
    for ($index = 0; $index -lt $count; $index++) {
        $time = $index / $sampleRate
        $value = & $generator $time $duration $index
        $samples.Add($value)
        $peak = [math]::Max($peak, [math]::Abs($value))
    }
    $scale = 0.76 / $peak
    $stream = [IO.File]::Create((Join-Path $destination $name))
    $writer = New-Object IO.BinaryWriter $stream
    try {
        $dataSize = $count * 2
        $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write(36 + $dataSize)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write(16)
        $writer.Write([int16]1)
        $writer.Write([int16]1)
        $writer.Write($sampleRate)
        $writer.Write($sampleRate * 2)
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write($dataSize)
        foreach ($sample in $samples) {
            $writer.Write([int16]([math]::Round($sample * $scale * 32767)))
        }
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

$script:noiseSeed = 190319
function Noise {
    $script:noiseSeed = (1103515245L * $script:noiseSeed + 12345L) -band 0x7fffffff
    return ($script:noiseSeed / 1073741824.0) - 1.0
}

Write-Effect 'melee_swing.wav' 0.22 {
    param($t, $d, $i)
    $frequency = 1250 - 900 * ($t / $d)
    $air = (Noise) * 0.38 * [math]::Pow(1 - $t / $d, 2)
    (0.58 * [math]::Sin(2 * [math]::PI * $frequency * $t) + $air) * (Envelope $t $d 0.008 0.10)
}

Write-Effect 'energy_charge.wav' 0.34 {
    param($t, $d, $i)
    $progress = $t / $d
    $frequency = 210 + 720 * $progress * $progress
    $pulse = 0.72 + 0.28 * [math]::Sin(2 * [math]::PI * 11 * $t)
    (0.64 * [math]::Sin(2 * [math]::PI * $frequency * $t) +
        0.24 * [math]::Sin(2 * [math]::PI * $frequency * 2.01 * $t)) * $pulse * (Envelope $t $d 0.018 0.045)
}

Write-Effect 'projectile_launch.wav' 0.24 {
    param($t, $d, $i)
    $progress = $t / $d
    $frequency = 1450 * [math]::Pow(0.19, $progress) + 120
    (0.70 * [math]::Sin(2 * [math]::PI * $frequency * $t) + (Noise) * 0.20) *
        (Envelope $t $d 0.004 0.11)
}

Write-Effect 'impact.wav' 0.19 {
    param($t, $d, $i)
    $progress = $t / $d
    $thump = [math]::Sin(2 * [math]::PI * (145 - 80 * $progress) * $t)
    ($thump * 0.85 + (Noise) * 0.34 * [math]::Pow(1 - $progress, 3)) * (Envelope $t $d 0.002 0.12)
}

Write-Effect 'hit_reaction.wav' 0.17 {
    param($t, $d, $i)
    $progress = $t / $d
    $metal = [math]::Sin(2 * [math]::PI * (620 - 310 * $progress) * $t) +
        0.44 * [math]::Sin(2 * [math]::PI * (1030 - 440 * $progress) * $t)
    $metal * (Envelope $t $d 0.003 0.10)
}

Write-Effect 'ko_power_down.wav' 0.58 {
    param($t, $d, $i)
    $progress = $t / $d
    $frequency = 690 * [math]::Pow(0.12, $progress) + 42
    $warble = 1 + 0.08 * [math]::Sin(2 * [math]::PI * 17 * $t)
    (0.72 * [math]::Sin(2 * [math]::PI * $frequency * $warble * $t) +
        0.16 * (Noise) * (1 - $progress)) * (Envelope $t $d 0.01 0.16)
}

Write-Effect 'victory_stinger.wav' 0.72 {
    param($t, $d, $i)
    $frequencies = @(523.25, 659.25, 783.99, 1046.50)
    $step = [math]::Min(3, [int]($t / 0.15))
    $local = $t - $step * 0.15
    $note = $frequencies[$step]
    $noteEnvelope = [math]::Exp(-4.2 * $local)
    $finish = if ($t -gt 0.45) { 0.42 * [math]::Sin(2 * [math]::PI * 523.25 * $t) } else { 0.0 }
    (0.66 * [math]::Sin(2 * [math]::PI * $note * $t) * $noteEnvelope + $finish) *
        (Envelope $t $d 0.005 0.12)
}

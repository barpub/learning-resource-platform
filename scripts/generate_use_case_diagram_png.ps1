$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$scriptDir = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location) 'scripts'
} else {
    $PSScriptRoot
}
$root = Resolve-Path (Join-Path $scriptDir '..')
$out = Join-Path $root 'docs\use-case-diagram.png'

$width = 3600
$height = 2400
$bitmap = New-Object System.Drawing.Bitmap($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

function New-Brush($html) {
    return New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($html))
}

function New-Pen($html, $size = 2) {
    return New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml($html), $size)
}

function New-Font($size, $style = [System.Drawing.FontStyle]::Regular) {
    return New-Object System.Drawing.Font('Microsoft YaHei', $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Draw-CenteredText($text, $rect, $font, $brush) {
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $graphics.DrawString($text, $font, $brush, $rect, $format)
    $format.Dispose()
}

function Draw-Actor($x, $y, $label) {
    $pen = New-Pen '#1f2937' 5
    $brush = New-Brush '#111827'
    $font = New-Font 34 ([System.Drawing.FontStyle]::Bold)
    $cx = $x + 70
    $graphics.DrawEllipse($pen, $cx - 32, $y, 64, 64)
    $graphics.DrawLine($pen, $cx, $y + 64, $cx, $y + 175)
    $graphics.DrawLine($pen, $cx - 74, $y + 104, $cx + 74, $y + 104)
    $graphics.DrawLine($pen, $cx, $y + 175, $cx - 72, $y + 270)
    $graphics.DrawLine($pen, $cx, $y + 175, $cx + 72, $y + 270)
    Draw-CenteredText $label ([System.Drawing.RectangleF]::new([float]($x - 25), [float]($y + 292), [float]190, [float]54)) $font $brush
    $pen.Dispose()
    $brush.Dispose()
    $font.Dispose()
}

function Draw-UseCase($key, $x, $y, $text) {
    $rect = [System.Drawing.RectangleF]::new([float]$x, [float]$y, [float]400, [float]90)
    $shadow = New-Brush '#d8e6f4'
    $fill = New-Brush '#f8fbff'
    $border = New-Pen '#2563a6' 3
    $textBrush = New-Brush '#102033'
    $font = New-Font 28
    $graphics.FillEllipse($shadow, $x + 8, $y + 10, 400, 90)
    $graphics.FillEllipse($fill, $rect)
    $graphics.DrawEllipse($border, $rect)
    Draw-CenteredText $text $rect $font $textBrush
    $script:useCases[$key] = @{
        X = [float]$x
        Y = [float]$y
        W = [float]400
        H = [float]90
    }
    $shadow.Dispose()
    $fill.Dispose()
    $border.Dispose()
    $textBrush.Dispose()
    $font.Dispose()
}

function Get-Point($key, $side) {
    $r = $script:useCases[$key]
    if ($side -eq 'L') { return [System.Drawing.PointF]::new($r.X, $r.Y + $r.H / 2) }
    if ($side -eq 'R') { return [System.Drawing.PointF]::new($r.X + $r.W, $r.Y + $r.H / 2) }
    if ($side -eq 'T') { return [System.Drawing.PointF]::new($r.X + $r.W / 2, $r.Y) }
    if ($side -eq 'B') { return [System.Drawing.PointF]::new($r.X + $r.W / 2, $r.Y + $r.H) }
    return [System.Drawing.PointF]::new($r.X + $r.W / 2, $r.Y + $r.H / 2)
}

function Draw-Line($x1, $y1, $x2, $y2) {
    $pen = New-Pen '#64748b' 2
    $graphics.DrawLine($pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
    $pen.Dispose()
}

function Draw-OpenArrowHead($from, $to, $pen) {
    $angle = [Math]::Atan2($to.Y - $from.Y, $to.X - $from.X)
    $size = 20
    $a1 = $angle + [Math]::PI * 0.82
    $a2 = $angle - [Math]::PI * 0.82
    $p1 = [System.Drawing.PointF]::new([float]($to.X + [Math]::Cos($a1) * $size), [float]($to.Y + [Math]::Sin($a1) * $size))
    $p2 = [System.Drawing.PointF]::new([float]($to.X + [Math]::Cos($a2) * $size), [float]($to.Y + [Math]::Sin($a2) * $size))
    $graphics.DrawLine($pen, $to, $p1)
    $graphics.DrawLine($pen, $to, $p2)
}

function Draw-DashedArrow($fromKey, $fromSide, $toKey, $toSide, $label) {
    $from = Get-Point $fromKey $fromSide
    $to = Get-Point $toKey $toSide
    $pen = New-Pen '#475569' 2
    $pen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
    $graphics.DrawLine($pen, $from, $to)
    Draw-OpenArrowHead $from $to $pen

    $font = New-Font 22
    $brush = New-Brush '#475569'
    $bg = New-Brush '#ffffff'
    $mx = ($from.X + $to.X) / 2
    $my = ($from.Y + $to.Y) / 2
    $labelRect = [System.Drawing.RectangleF]::new([float]($mx - 62), [float]($my - 18), [float]124, [float]36)
    $graphics.FillRectangle($bg, $labelRect)
    Draw-CenteredText $label $labelRect $font $brush

    $pen.Dispose()
    $font.Dispose()
    $brush.Dispose()
    $bg.Dispose()
}

function Draw-Generalization($x1, $y1, $x2, $y2) {
    $pen = New-Pen '#334155' 3
    $graphics.DrawLine($pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
    $triangle = @(
        [System.Drawing.PointF]::new([float]$x2, [float]$y2),
        [System.Drawing.PointF]::new([float]($x2 - 18), [float]($y2 + 38)),
        [System.Drawing.PointF]::new([float]($x2 + 18), [float]($y2 + 38))
    )
    $fill = New-Brush '#ffffff'
    $graphics.FillPolygon($fill, $triangle)
    $graphics.DrawPolygon($pen, $triangle)
    $font = New-Font 22
    $brush = New-Brush '#334155'
    Draw-CenteredText '继承' ([System.Drawing.RectangleF]::new([float]($x2 + 18), [float](($y1 + $y2) / 2 - 18), [float]76, [float]36)) $font $brush
    $pen.Dispose()
    $fill.Dispose()
    $font.Dispose()
    $brush.Dispose()
}

$bg = New-Brush '#ffffff'
$graphics.FillRectangle($bg, 0, 0, $width, $height)
$bg.Dispose()

$titleBrush = New-Brush '#0f172a'
$mutedBrush = New-Brush '#475569'
$titleFont = New-Font 56 ([System.Drawing.FontStyle]::Bold)
$subFont = New-Font 28
Draw-CenteredText '学习资源共享平台用例图' ([System.Drawing.RectangleF]::new([float]0, [float]22, [float]$width, [float]72)) $titleFont $titleBrush
Draw-CenteredText '参与者：游客、普通用户、管理员；包含资源浏览、上传管理、个人统计与后台管理等核心业务' ([System.Drawing.RectangleF]::new([float]0, [float]92, [float]$width, [float]44)) $subFont $mutedBrush

$systemPen = New-Pen '#1d4ed8' 4
$systemBrush = New-Brush '#f8fafc'
$graphics.FillRectangle($systemBrush, 560, 160, 2880, 2140)
$graphics.DrawRectangle($systemPen, 560, 160, 2880, 2140)
$systemFont = New-Font 34 ([System.Drawing.FontStyle]::Bold)
Draw-CenteredText '学习资源共享平台' ([System.Drawing.RectangleF]::new([float]540, [float]174, [float]310, [float]50)) $systemFont $titleBrush

$script:useCases = @{}

# Public use cases
Draw-UseCase 'home' 800 270 '浏览首页'
Draw-UseCase 'search' 800 395 '搜索资源'
Draw-UseCase 'category' 800 520 '按分类浏览资源'
Draw-UseCase 'detail' 800 645 '查看资源详情'
Draw-UseCase 'preview' 800 770 '在线预览资源'
Draw-UseCase 'register' 800 895 '注册账号'
Draw-UseCase 'login' 800 1020 '登录系统'

# User use cases
Draw-UseCase 'upload' 1460 270 '上传资源'
Draw-UseCase 'download' 1460 395 '下载资源'
Draw-UseCase 'favorite' 1460 520 '收藏资源'
Draw-UseCase 'unfavorite' 1460 645 '取消收藏'
Draw-UseCase 'share' 1460 770 '分享资源链接'
Draw-UseCase 'comment' 1460 895 '评论资源'
Draw-UseCase 'mine' 1460 1020 '查看我的工作台'

# Upload and mine included or extended use cases
Draw-UseCase 'batchUpload' 2240 210 '批量上传文件/文件夹'
Draw-UseCase 'createFolder' 2240 340 '创建子文件夹'
Draw-UseCase 'treeManage' 2240 470 '管理目录树'
Draw-UseCase 'editResource' 2920 420 '修改资源信息'
Draw-UseCase 'moveResource' 2920 570 '移动资源位置'
Draw-UseCase 'deleteOwn' 2920 720 '删除自己上传的资源'
Draw-UseCase 'uploadStats' 2240 965 '查看上传统计'
Draw-UseCase 'viewStats' 2240 1095 '查看浏览统计'
Draw-UseCase 'favoriteStats' 2240 1225 '查看收藏统计'
Draw-UseCase 'downloadTrend' 2240 1355 '查看下载趋势'
Draw-UseCase 'manageFavorites' 2240 1485 '管理我的收藏'

# Admin use cases
Draw-UseCase 'audit' 1080 1590 '审核资源'
Draw-UseCase 'manageResources' 1080 1720 '管理全部资源'
Draw-UseCase 'manageUsers' 1080 1850 '管理用户'
Draw-UseCase 'manageCategories' 1080 1980 '管理分类'
Draw-UseCase 'manageComments' 1080 2110 '管理评论'
Draw-UseCase 'deleteAny' 1760 1720 '删除任意资源'
Draw-UseCase 'userStatus' 1760 1850 '启用/禁用用户'

# Actors
Draw-Actor 145 300 '游客'
Draw-Actor 145 870 '普通用户'
Draw-Actor 145 1500 '管理员'

# Actor associations
$visitorX = 285
$visitorY = 430
Draw-Line $visitorX $visitorY 760 314
Draw-Line $visitorX $visitorY 800 440
Draw-Line $visitorX $visitorY 800 565
Draw-Line $visitorX $visitorY 800 690
Draw-Line $visitorX $visitorY 800 815
Draw-Line $visitorX $visitorY 800 940
Draw-Line $visitorX $visitorY 800 1065

$userX = 285
$userY = 1000
Draw-Line $userX $userY 1460 315
Draw-Line $userX $userY 1460 440
Draw-Line $userX $userY 1460 565
Draw-Line $userX $userY 1460 690
Draw-Line $userX $userY 1460 815
Draw-Line $userX $userY 1460 940
Draw-Line $userX $userY 1460 1065

$adminX = 285
$adminY = 1630
Draw-Line $adminX $adminY 1080 1635
Draw-Line $adminX $adminY 1080 1765
Draw-Line $adminX $adminY 1080 1895
Draw-Line $adminX $adminY 1080 2025
Draw-Line $adminX $adminY 1080 2155
Draw-Generalization 285 1470 285 1210
Draw-Generalization 285 850 285 665

# Include and extend relationships
Draw-DashedArrow 'batchUpload' 'L' 'upload' 'R' '<<extend>>'
Draw-DashedArrow 'createFolder' 'L' 'upload' 'R' '<<extend>>'
Draw-DashedArrow 'treeManage' 'L' 'upload' 'R' '<<include>>'
Draw-DashedArrow 'treeManage' 'R' 'editResource' 'L' '<<include>>'
Draw-DashedArrow 'treeManage' 'R' 'moveResource' 'L' '<<include>>'
Draw-DashedArrow 'treeManage' 'R' 'deleteOwn' 'L' '<<include>>'

Draw-DashedArrow 'mine' 'R' 'uploadStats' 'L' '<<include>>'
Draw-DashedArrow 'mine' 'R' 'viewStats' 'L' '<<include>>'
Draw-DashedArrow 'mine' 'R' 'favoriteStats' 'L' '<<include>>'
Draw-DashedArrow 'mine' 'R' 'downloadTrend' 'L' '<<include>>'
Draw-DashedArrow 'mine' 'R' 'manageFavorites' 'L' '<<include>>'

Draw-DashedArrow 'manageResources' 'T' 'audit' 'B' '<<include>>'
Draw-DashedArrow 'manageResources' 'R' 'deleteAny' 'L' '<<include>>'
Draw-DashedArrow 'manageUsers' 'R' 'userStatus' 'L' '<<include>>'

$legendFont = New-Font 24
$legendBrush = New-Brush '#334155'
$legendBg = New-Brush '#f1f5f9'
$legendPen = New-Pen '#cbd5e1' 2
$legendRect = [System.Drawing.RectangleF]::new([float]620, [float]2230, [float]1320, [float]46)
$graphics.FillRectangle($legendBg, $legendRect)
$graphics.DrawRectangle($legendPen, 620, 2230, 1320, 46)
Draw-CenteredText '说明：实线表示参与者关联；空心三角表示参与者泛化；虚线箭头表示 <<include>> 或 <<extend>> 关系。' $legendRect $legendFont $legendBrush

$bitmap.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

$graphics.Dispose()
$bitmap.Dispose()
$systemPen.Dispose()
$systemBrush.Dispose()
$titleBrush.Dispose()
$mutedBrush.Dispose()
$titleFont.Dispose()
$subFont.Dispose()
$systemFont.Dispose()
$legendFont.Dispose()
$legendBrush.Dispose()
$legendBg.Dispose()
$legendPen.Dispose()

Write-Output $out

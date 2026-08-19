param(
    [string]$Out = "D:\H-smart\tmp\category-images-300",
    [int]$Total = 300,
    [int]$MinWidth = 800,
    [int]$MinHeight = 600
)

$backend = if ($env:PEXELS_API_KEY) { "mixed" } else { "openverse" }

python "D:\H-smart\scripts\google_image_tool.py" `
  --backend $backend `
  --out $Out `
  --total $Total `
  --max-pages 24 `
  --page-size 20 `
  --min-width $MinWidth `
  --min-height $MinHeight `
  --max-edge 2200 `
  --jpeg-quality 90 `
  --pause 0.03

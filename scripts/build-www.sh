#!/usr/bin/env bash
# Wraps web/index.html (artifact-style page) into a full HTML document for the app's assets.
set -euo pipefail
cd "$(dirname "$0")/.."
{
  echo '<!doctype html><html lang="en"><head><meta charset="utf-8">'
  echo '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
  echo '<style>:root{padding-top:env(safe-area-inset-top);padding-bottom:env(safe-area-inset-bottom)}body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style>'
  echo '</head><body>'
  cat web/index.html
  echo '</body></html>'
} > app/src/main/assets/www/index.html
echo "Built app/src/main/assets/www/index.html"

# How to publish this repo to GitHub → get 4 native plugin ZIPs automatically

You already created https://github.com/itz1508/replay-state-memory .

## One-time push

Windows PowerShell (recommended path):

```powershell
# 1. Extract this ZIP into an empty folder
cd $HOME
mkdir replay-state-memory
cd replay-state-memory
# unzip the download here — Windows Explorer or `tar -xf` works

# 2. Point git at your repo
git init
git remote add origin https://github.com/itz1508/replay-state-memory.git
git branch -M main

# 3. Add and push everything
git add -A
git commit -m "Initial commit: rsm-jetbrains 0.2.0 source"
git push -u origin main
```

That single `push` triggers `.github/workflows/build.yml`.

## Watch the build

- Open https://github.com/itz1508/replay-state-memory/actions
- Click the latest run (labeled "build")
- Wait ~5 minutes for it to complete

## Download the four native plugin ZIPs

Once the run turns green:

1. Scroll down to the **Artifacts** section of the run summary
2. Click **rsm-jetbrains-native-variants** to download a folder containing:
   - `rsm-jetbrains-0.2.0-windows-x64.zip`  ← what you'll install on Windows
   - `rsm-jetbrains-0.2.0-macos-x64.zip`
   - `rsm-jetbrains-0.2.0-macos-arm64.zip`
   - `rsm-jetbrains-0.2.0-linux-x64.zip`

## Install into IntelliJ

`Settings → Plugins → gear ⚙ → Install Plugin from Disk...` → pick the ZIP for your OS.

## Optional: tag a release to auto-publish a GitHub Release

```powershell
git tag v0.2.0
git push origin v0.2.0
```

The workflow will run again and this time also attach the four ZIPs to a new
GitHub Release at https://github.com/itz1508/replay-state-memory/releases/tag/v0.2.0 ,
so the ZIPs get permanent download URLs.

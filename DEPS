include_rules = [
  "+content/public",

  "+crypto",
  "+net",
  "+sandbox",
  "+skia",
  "+ui",
  "+v8",
  "+webkit",

  # Allow inclusion of third-party code.
  "+third_party/skia",
  "+third_party/WebKit/public/platform",
  "+third_party/WebKit/public/web",

  # Files generated during Crosswalk build.
  "+grit/xwalk_resources.h",
]

vars = {
}

deps = {
}

hooks = [
  {
    # Generate .gclient-mogo for ChromeView's dependencies.
    "name": "generate-gclient-mogo",
    "pattern": ".",
    "action": ["python", "src/chromeview/tools/generate_gclient-mogo.py"],
  },
  #{
  #  # Fetch ChromeView dependencies.
  #  "name": "fetch-deps",
  #  "pattern": ".",
  #  "action": ["python", "src/chromeview/tools/fetch_deps.py", "-v"],
  #},
  #{
  #  # Apply patches.
  #  "name": "patcher",
  #  "pattern": ".",
  #  "action": ["python", "src/chromeview/tools/patcher.py", "--patch-config", "src/chromeview/patch/patch.cfg"],
  #},
  #{
  #  # A change to a .gyp, .gypi, or to GYP itself should run the generator.
  #  "name": "gyp-mogo",
  #  "pattern": ".",
  #  "action": ["python", "src/chromeview/gyp_mogo"],
  #}
]
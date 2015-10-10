#!/usr/bin/env python

import optparse
import os
import shutil
import sys

def AddSyncOption(option_parser):
  option_parser.add_option('--debug', action='store_const', const='Debug',
                           dest='build_type',
                           default=os.environ.get('BUILDTYPE', 'Debug'),
                           help='If set, sync files under out/Debug. '
                           'Default is env var BUILDTYPE or Debug')
  option_parser.add_option('--release', action='store_const', const='Release',
                           dest='build_type',
                           help='If set, sync files under out/Release. '
                           'Default is env var BUILDTYPE or Debug.')
  option_parser.add_option('--chromium_root', dest='chromium_root',
                           help='The root folder for chromium source. Default is the parent folder of this script')

  option_parser.add_option('--chromeview_java', dest='chromeview_java',
                           help='The root folder for chromeview java source. Default is java subfolder')

def SyncJars(options):
  chromium_jars = ["android_webview_java.jar", "base_java.jar", "content_java.jar", "media_java.jar",
    "mojo_bindings_java.jar", "mojo_public_java.jar", "mojo_system_java.jar",
    "net_java.jar", "ui_java.jar", "web_contents_delegate_android_java.jar"]
  chromium_jars_dir = options.chromium_root + "/out/" + options.build_type + "/lib.java/"
  for jar in chromium_jars:
    shutil.copy(chromium_jars_dir + jar, options.chromeview_java + "/libs/")
def SyncSos(options):
  chromium_sos = ["libchromeview.so"]
  chromium_sos_dir = options.chromium_root + "/out/" + options.build_type + "/lib/"
  for so in chromium_sos:
    shutil.copy(chromium_sos_dir + so, options.chromeview_java + "/libs/armeabi-v7a/")

def main(argv):
  parser = optparse.OptionParser()
  parser.set_usage("usage: %prog [options]")
  AddSyncOption(parser)
  options, args = parser.parse_args(argv)

  if not options.chromium_root:
    options.chromium_root = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))

  if not options.chromeview_java:
    options.chromeview_java = os.path.abspath(os.path.join(os.path.dirname(__file__), 'java'))

  SyncJars(options)
  SyncSos(options)

if __name__ == '__main__':
  sys.exit(main(sys.argv))
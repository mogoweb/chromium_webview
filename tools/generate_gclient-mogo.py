#!/usr/bin/env python
#
# Copyright (c) 2013 mogoweb. All rights reserved.
# Copyright (c) 2013 Intel Corporation. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This script is responsible for generating .gclient-mogo in the top-level
source directory from .DEPS.mogo.

User-configurable values such as |cache_dir| are fetched from .gclient instead.
"""

import logging
import optparse
import os
import pprint
import sys

CHROMEVIEW_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GCLIENT_ROOT = os.path.dirname(os.path.dirname(CHROMEVIEW_ROOT))

def ParseGClientConfig():
  """
  Parses the top-level .gclient file (NOT .gclient-mogo) and returns the
  values set there as a dictionary.
  """
  with open(os.path.join(GCLIENT_ROOT, '.gclient')) as dot_gclient:
    config = {}
    exec(dot_gclient, config)
  return config

def GenerateGClientMogo():
  with open(os.path.join(CHROMEVIEW_ROOT, 'DEPS.mogo')) as deps_file:
    deps_contents = deps_file.read()

  deps_contents += 'target_os = [\'android\']\n'

  gclient_config = ParseGClientConfig()
  cache_dir = gclient_config.get('cache_dir')
  deps_contents += 'cache_dir = %s\n' % pprint.pformat(cache_dir)

  with open(os.path.join(GCLIENT_ROOT, '.gclient-mogo'), 'w') as gclient_file:
    gclient_file.write(deps_contents)

def main():
  GenerateGClientMogo()


if __name__ == '__main__':
  main()

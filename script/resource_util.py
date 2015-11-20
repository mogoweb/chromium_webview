#!/usr/bin/env python
#
# Copyright (c) 2015 The mogoweb project. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from xml.etree import ElementTree as et

def remove_duplicated_strings(f1, f2):
    roots1 = et.parse(f1).getroot()
    roots2 = et.parse(f2).getroot()

    name_in_f1 = []
    for string in roots1.findall('string'):
        name = string.get('name')
        name_in_f1.append(name)

    for string in roots2.findall('string'):
        name = string.get('name')
        if name in name_in_f1:
            print("duplicated string:%s found" % name)

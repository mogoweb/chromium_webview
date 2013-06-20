#!/usr/bin/python2.4
#
# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
Creates the list of search engines

The created list is placed in the res/values-<locale> directory. Also updates
res/values/all_search_engines.xml if required with new data.

Usage: get_search_engines.py

Copyright (C) 2010 The Android Open Source Project
"""

import os
import re
import sys
import urllib
from xml.dom import minidom

# Locales to generate search engine lists for
locales = ["cs-CZ", "da-DK", "de-AT", "de-CH", "de-DE", "el-GR", "en-AU",
    "en-GB", "en-IE", "en-NZ", "en-SG", "en-ZA", "es-ES", "fr-BE", "fr-FR",
    "it-IT", "ja-JP", "ko-KR", "nb-NO", "nl-BE", "nl-NL", "pl-PL", "pt-PT",
    "pt-BR", "ru-RU", "sv-SE", "tr-TR", "zh-CN", "zh-HK", "zh-MO", "zh-TW"]

google_data = ["google", "Google", "google.com",
  "http://www.google.com/favicon.ico",
  "http://www.google.com/search?ie={inputEncoding}&amp;source=android-browser&amp;q={searchTerms}",
  "UTF-8",
  "http://www.google.com/complete/search?client=android&amp;q={searchTerms}"]

class SearchEngineManager(object):
  """Manages list of search engines and creates locale specific lists.

  The main method useful for the caller is generateListForLocale(), which
  creates a locale specific donottranslate-search_engines.xml file.
  """

  def __init__(self):
    """Inits SearchEngineManager with relevant search engine data.

    The search engine data is downloaded from the Chrome source repository.
    """
    self.chrome_data = urllib.urlopen(
        'http://src.chromium.org/viewvc/chrome/trunk/src/chrome/'
        'browser/search_engines/template_url_prepopulate_data.cc').read()
    if self.chrome_data.lower().find('repository not found') != -1:
      print 'Unable to get Chrome source data for search engine list.\nExiting.'
      sys.exit(2)

    self.resdir = os.path.normpath(os.path.join(sys.path[0], '../res'))

    self.all_engines = set()

  def getXmlString(self, str):
    """Returns an XML-safe string for the given string.

    Given a string from the search engine data structure, convert it to a
    string suitable to write to our XML data file by stripping away NULLs,
    unwanted quotes, wide-string declarations (L"") and replacing C-style
    unicode characters with XML equivalents.
    """
    str = str.strip()
    if str.upper() == 'NULL':
      return ''

    if str.startswith('L"'):
      str = str[2:]
    if str.startswith('@') or str.startswith('?'):
      str = '\\' + str

    str = str.strip('"')
    str = str.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    str = str.replace('"', '&quot;').replace('\'', '&apos;')
    str = re.sub(r'\\x([a-fA-F0-9]{1,4})', r'&#x\1;', str)

    return str

  def getEngineData(self, name):
    """Returns an array of strings describing the specified search engine.

    The returned strings are in the same order as in the Chrome source data file
    except that the internal name of the search engine is inserted at the
    beginning of the list.
    """

    if name == "google":
      return google_data

    # Find the first occurance of this search engine name in the form
    # " <name> =" in the chrome data file.
    re_exp = '\s' + name + '\s*='
    search_obj = re.search(re_exp, self.chrome_data)
    if not search_obj:
      print ('Unable to find data for search engine ' + name +
             '. Please check the chrome data file for format changes.')
      return None

    # Extract the struct declaration between the curly braces.
    start_pos = self.chrome_data.find('{', search_obj.start()) + 1;
    end_pos = self.chrome_data.find('};', start_pos);
    engine_data_str = self.chrome_data[start_pos:end_pos]

    # Remove c++ style '//' comments at the ends of each line
    engine_data_lines = engine_data_str.split('\n')
    engine_data_str = ""
    for line in engine_data_lines:
        start_pos = line.find(' // ')
        if start_pos != -1:
            line = line[:start_pos]
        engine_data_str = engine_data_str + line + '\n'

    # Join multiple line strings into a single string.
    engine_data_str = re.sub('\"\s+\"', '', engine_data_str)
    engine_data_str = re.sub('\"\s+L\"', '', engine_data_str)
    engine_data_str = engine_data_str.replace('"L"', '')

    engine_data = engine_data_str.split(',')
    for i in range(len(engine_data)):
      engine_data[i] = self.getXmlString(engine_data[i])

    # If the last element was an empty string (due to an extra comma at the
    # end), ignore it.
    if not engine_data[len(engine_data) - 1]:
      engine_data.pop()

    engine_data.insert(0, name)

    return engine_data

  def getSearchEnginesForCountry(self, country):
    """Returns the list of search engine names for the given country.

    The data comes from the Chrome data file.
    """
    # The Chrome data file has an array defined with the name 'engines_XX'
    # where XX = country.
    pos = self.chrome_data.find('engines_' + country)
    if pos == -1:
      print ('Unable to find search engine data for country ' + country + '.')
      return

    # Extract the text between the curly braces for this array declaration
    engines_start = self.chrome_data.find('{', pos) + 1;
    engines_end = self.chrome_data.find('}', engines_start);
    engines_str = self.chrome_data[engines_start:engines_end]

    # Remove embedded /**/ style comments, white spaces, address-of operators
    # and the trailing comma if any.
    engines_str = re.sub('\/\*.+\*\/', '', engines_str)
    engines_str = re.sub('\s+', '', engines_str)
    engines_str = engines_str.replace('&','')
    engines_str = engines_str.rstrip(',')

    # Split the array into it's elements
    engines = engines_str.split(',')

    return engines

  def writeAllEngines(self):
    """Writes all search engines to the all_search_engines.xml file.
    """

    all_search_engines_path = os.path.join(self.resdir, 'values/all_search_engines.xml')

    text = []

    for engine_name in self.all_engines:
      engine_data = self.getEngineData(engine_name)
      text.append('  <string-array name="%s" translatable="false">\n' % (engine_data[0]))
      for i in range(1, 7):
        text.append('    <item>%s</item>\n' % (engine_data[i]))
      text.append('  </string-array>\n')
      print engine_data[1] + " added to all_search_engines.xml"

    self.generateXmlFromTemplate(os.path.join(sys.path[0], 'all_search_engines.template.xml'),
        all_search_engines_path, text)

  def generateDefaultList(self):
    self.writeEngineList(os.path.join(self.resdir, 'values'), "default")

  def generateListForLocale(self, locale):
    """Creates a new locale specific donottranslate-search_engines.xml file.

    The new file contains search engines specific to that country. If required
    this function updates all_search_engines.xml file with any new search
    engine data necessary.
    """
    separator_pos = locale.find('-')
    if separator_pos == -1:
      print ('Locale must be of format <language>-<country>. For e.g.'
             ' "es-US" or "en-GB"')
      return

    language = locale[0:separator_pos]
    country = locale[separator_pos + 1:].upper()
    dir_path = os.path.join(self.resdir, 'values-' + language + '-r' + country)

    self.writeEngineList(dir_path, country)

  def writeEngineList(self, dir_path, country):
    if os.path.exists(dir_path) and not os.path.isdir(dir_path):
      print "File exists in output directory path " + dir_path + ". Please remove it and try again."
      return

    engines = self.getSearchEnginesForCountry(country)
    if not engines:
      return
    for engine in engines:
      self.all_engines.add(engine)

    # Create the locale specific search_engines.xml file. Each
    # search_engines.xml file has a hardcoded list of 7 items. If there are less
    # than 7 search engines for this country, the remaining items are marked as
    # enabled=false.
    text = []
    text.append('  <string-array name="search_engines" translatable="false">\n');
    for engine in engines:
      engine_data = self.getEngineData(engine)
      name = engine_data[0]
      text.append('    <item>%s</item>\n' % (name))
    text.append('  </string-array>\n');

    self.generateXmlFromTemplate(os.path.join(sys.path[0], 'search_engines.template.xml'),
        os.path.join(dir_path, 'donottranslate-search_engines.xml'),
        text)

  def generateXmlFromTemplate(self, template_path, out_path, text):
    # Load the template file and insert the new contents before the last line.
    template_text = open(template_path).read()
    pos = template_text.rfind('\n', 0, -2) + 1
    contents = template_text[0:pos] + ''.join(text) + template_text[pos:]

    # Make sure what we have created is valid XML :) No need to check for errors
    # as the script will terminate with an exception if the XML was malformed.
    engines_dom = minidom.parseString(contents)

    dir_path = os.path.dirname(out_path)
    if not os.path.exists(dir_path):
      os.makedirs(dir_path)
      print 'Created directory ' + dir_path
    file = open(out_path, 'w')
    file.write(contents)
    file.close()
    print 'Wrote ' + out_path

if __name__ == "__main__":
  manager = SearchEngineManager()
  manager.generateDefaultList()
  for locale in locales:
    manager.generateListForLocale(locale)
  manager.writeAllEngines()

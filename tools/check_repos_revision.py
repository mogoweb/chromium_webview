#!/usr/bin/env python

# Copyright (c) 2014 China LianTong. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import re
import sys
import optparse
import os
import subprocess

_GIT_SVN_ID_REGEX = re.compile(r'.*git-svn-id:\s*([^@]*)@([0-9]+)', re.DOTALL)

def RunGitCommand(directory, command):
  """
  Launches git subcommand.

  Errors are swallowed.

  Returns:
    A process object or None.
  """
  command = ['git'] + command
  # Force shell usage under cygwin. This is a workaround for
  # mysterious loss of cwd while invoking cygwin's git.
  # We can't just pass shell=True to Popen, as under win32 this will
  # cause CMD to be used, while we explicitly want a cygwin shell.
  if sys.platform == 'cygwin':
    command = ['sh', '-c', ' '.join(command)]
  try:
    proc = subprocess.Popen(command,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,
                            cwd=directory,
                            shell=(sys.platform=='win32'))
    return proc
  except OSError:
    return None

def FetchGitSVNRevision(directory):
  """
  Fetch the Subversion revision through Git.

  Errors are swallowed.

  Returns:
    the Subversion revision.
  """
  proc = RunGitCommand(directory, ['log', '-1',
                                   '--grep=git-svn-id', '--format=%b'])
  if proc:
    output = proc.communicate()[0].strip()
    if proc.returncode == 0 and output:
      # Extract the latest SVN revision and the SVN URL.
      # The target line is the last "git-svn-id: ..." line like this:
      # git-svn-id: svn://svn.chromium.org/chrome/trunk/src@85528 0039d316....
      match = _GIT_SVN_ID_REGEX.search(output)
      if match:
        revision = match.group(2)
        return revision
  return None

def CheckRepoVersion(deps_file, chromium_dir):
  """
  compare the git svn id of third party repositories with the revsion defined in DEPS.

  Returns:
    True if all of repositories's git svn id equal to the revsion defined in DEPS.
  """

  # parse the DEPS file and check revsion
  return ParseDepsFileAndCheckRevision(deps_file, chromium_dir)

def ParseDepsFileAndCheckRevision(deps_file, chromium_dir):
  if not os.path.exists(deps_file):
    raise IOError('Deps file does not exist (%s).' % deps_file)

  parser = DEPSParser(chromium_dir)
  submodules = parser.ParseFile(deps_file)
  return parser.CheckModules(submodules)

class DEPSParser:
  def __init__(self, chromium_dir=''):
    self.global_scope = {
      'Var': self.Lookup,
      'deps_os': {},
    }
    self.local_scope = {}
    self.chromium_dir = chromium_dir

  def Lookup(self, var_name):
    return self.local_scope["vars"][var_name]

  def CreateSubmodulesFromScope(self, scope, os):
    submodules = []
    for dep in scope:
      if (type(scope[dep]) == str):
        repo_rev = scope[dep].split('@')
        repo = repo_rev[0]
        rev = repo_rev[1]
        subdir = dep
        if subdir.startswith('src/'):
          subdir = subdir[4:]
        if subdir.startswith('src'):
          # Ignore the information about src.
          continue
        if len(rev) == 40: # Length of a git shasum
          # ignore git sub module
          continue
        submodule = Submodule(subdir, os, rev)
        submodules.append(submodule)
    return submodules

  def Parse(self, deps_content):
    exec(deps_content, self.global_scope, self.local_scope)

    submodules = []
    submodules.extend(self.CreateSubmodulesFromScope(self.local_scope['deps'], 'all'))
    for os_dep in self.local_scope['deps_os']:
      if os_dep in ['unix', 'android']:
        submodules.extend(self.CreateSubmodulesFromScope(self.local_scope['deps_os'][os_dep], os_dep))

    return submodules

  def CheckModules(self, submodules):
    ret = True
    for submodule in submodules:
      r = submodule.CheckRevision(self.chromium_dir)
      ret = ret and r

    return ret

  def ParseFile(self, deps_file_name):
    deps_file = open(deps_file_name)
    deps_content = deps_file.read().decode('utf-8')
    deps_file.close()
    return self.Parse(deps_content)

class Submodule:
  def __init__(self, path='', os=[], revision=''):
    self.path = path
    self.os = os
    self.revision = revision

  def CheckRevision(self, chromium_src):
    repo_path = os.path.join(chromium_src, self.path)
    git_svn_rev = FetchGitSVNRevision(repo_path)
    if git_svn_rev is not None and git_svn_rev != self.revision:
      print "repo %s has wrong revision: expect %s but got %s" % (self.path, self.revision, git_svn_rev)
      return False

    return True

def main(argv=None):
  if argv is None:
    argv = sys.argv

  parser = optparse.OptionParser(usage="check_repos_revision.py [options]")
  parser.add_option("-f", "--deps_file", metavar="FILE",
                    help="DEPS file of specified release")
  parser.add_option("-s", "--chromium-dir", metavar="DIR",
                    help="the directory of chromium src")
  opts, args = parser.parse_args(argv[1:])

  deps_file = opts.deps_file
  if deps_file is None:
    sys.stderr.write('you should specify the DEPS: %r\n\n' % args)
    parser.print_help()
    sys.exit(2)
  
  if opts.chromium_dir:
    chromium_dir = opts.chromium_dir
  else:
    chromium_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir, os.pardir))

  ok = CheckRepoVersion(deps_file, chromium_dir)

if __name__ == '__main__':
  sys.exit(main())
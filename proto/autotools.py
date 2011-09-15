import subprocess

def run(spec):

  confs = spec['configurations']

  for i, conf in enumerate(confs):

    # it's autotools, so you need to configure
    subprocess.call('autogen.sh')

    dirname = "conf" + str(i)
    print "configuring in " + dirname
    os.mkdir(dirname)
    os.chdir(dirname)
    subprocess.call('../configure ' + conf)
    subprocess.call('make')
    subprocess.call('make check')

    os.chdir('..')


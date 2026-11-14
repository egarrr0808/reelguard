import contextlib
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import check_public


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.override = patch.object(check_public, 'ROOT', self.root)
        self.override.start()
        self.names = ['README.md', 'LICENSE', '.gitignore', 'public-files.txt']
        for name in self.names:
            (self.root / name).write_text('source fixture\n')
        self.write_list()

    def tearDown(self):
        self.override.stop()
        self.temp.cleanup()

    def write_list(self):
        (self.root / 'public-files.txt').write_text('\n'.join(self.names) + '\n')

    def test_archive_excludes_unlisted_private_artifacts(self):
        (self.root / 'private.keystore').write_bytes(b'private fixture')
        (self.root / 'base.apk').write_bytes(b'proprietary fixture')
        with patch('sys.argv', ['check_public.py', '--archive']), contextlib.redirect_stdout(io.StringIO()):
            check_public.main()
        with tarfile.open(self.root / 'build/reelguard-source.tar.gz') as archive:
            self.assertEqual(set(archive.getnames()), {'reelguard/' + name for name in self.names})

    def test_allowlisted_binary_rejected(self):
        self.names.append('base.apk')
        self.write_list()
        (self.root / 'base.apk').write_bytes(b'fixture')
        with self.assertRaisesRegex(ValueError, 'Private/binary'):
            list(check_public.public_files())

    def test_path_traversal_rejected(self):
        self.names.append('../outside.java')
        self.write_list()
        with self.assertRaisesRegex(ValueError, 'Invalid publication path'):
            list(check_public.public_files())

    def test_symlink_rejected(self):
        (self.root / 'actual.txt').write_text('fixture')
        (self.root / 'linked.txt').symlink_to(self.root / 'actual.txt')
        self.names.append('linked.txt')
        self.write_list()
        with self.assertRaisesRegex(ValueError, 'ordinary source file'):
            list(check_public.public_files())

    def test_token_rejected(self):
        (self.root / 'README.md').write_text('ghp_' + 'x' * 36)
        with self.assertRaisesRegex(ValueError, 'Secret or workstation path'):
            list(check_public.public_files())

    def test_personal_host_path_rejected(self):
        (self.root / 'README.md').write_text('/' + 'home' + '/someone/project/')
        with self.assertRaisesRegex(ValueError, 'Secret or workstation path'):
            list(check_public.public_files())


if __name__ == '__main__':
    unittest.main()

import unittest
import hashlib
import struct
import zlib
from build_patch import patch_dex
from verify_patch import verify_content, is_signature

class PatchTests(unittest.TestCase):
    def setUp(self):
        self.original=bytearray(256)
        self.original[:8]=b'dex\n035\0'
        self.original[128:130]=b'\x12\x10'
        self.original[12:32]=hashlib.sha1(self.original[32:]).digest()
        struct.pack_into('<I',self.original,8,zlib.adler32(self.original[12:]) & 0xffffffff)
        self.original=bytes(self.original)
        self.patch={'offset':128,'before':'1210','after':'1200','method':'test'}
    def test_valid_patch_and_independent_verifier(self):
        result=patch_dex(self.original,[self.patch])
        verify_content(self.original,result,[self.patch])
        self.assertEqual(result[128:130],b'\x12\0')
    def test_wrong_original_rejected(self):
        with self.assertRaises(ValueError): patch_dex(self.original,[dict(self.patch,before='ffff')])
    def test_overlapping_patch_rejected(self):
        with self.assertRaises(ValueError): patch_dex(self.original,[self.patch,self.patch])
    def test_length_changes_rejected(self):
        with self.assertRaises(ValueError): patch_dex(self.original,[dict(self.patch,after='12000000')])
    def test_header_write_rejected(self):
        with self.assertRaises(ValueError): patch_dex(self.original,[dict(self.patch,offset=20)])
    def test_outside_patch_modification_rejected(self):
        result=bytearray(patch_dex(self.original,[self.patch]));result[160]=1
        with self.assertRaises(AssertionError): verify_content(self.original,bytes(result),[self.patch])
    def test_unpatched_positive_control_rejected(self):
        with self.assertRaises(AssertionError): verify_content(self.original,self.original,[self.patch])
    def test_checksum_corruption_rejected(self):
        result=bytearray(patch_dex(self.original,[self.patch]));result[8]^=1
        with self.assertRaises(AssertionError): verify_content(self.original,bytes(result),[self.patch])
    def test_signature_metadata_allowlist_is_narrow(self):
        self.assertTrue(is_signature('stamp-cert-sha256'))
        self.assertTrue(is_signature('META-INF/CERT.RSA'))
        self.assertFalse(is_signature('assets/stamp-cert-sha256'))
        self.assertFalse(is_signature('AndroidManifest.xml'))
        self.assertFalse(is_signature('META-INF/androidx.core.version'))
if __name__=='__main__': unittest.main()

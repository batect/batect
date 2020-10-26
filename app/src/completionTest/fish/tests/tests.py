#! /usr/bin/env python3

import unittest


class FishCompletionTests(unittest.TestCase):
    def __init__(self, methodName):
        super().__init__(methodName)
        self.maxDiff = None


if __name__ == '__main__':
    unittest.main()

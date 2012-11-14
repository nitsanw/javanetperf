#!/usr/bin/python

import math
import unittest

import statistics


class FindRootTest(unittest.TestCase):
    def testSimple(self):
        v = statistics.findRoot(2, 0, 15, math.sqrt)
        assert abs(math.sqrt(v) - 2) < statistics.ACCURACY


class InverseTTest(unittest.TestCase):
    def testSimple(self):
        # df: 1, p: 0.95 = 6.31375
        v = statistics.InverseStudentT(1, 0.95)
        
        assert abs(v - 6.31375) < statistics.ACCURACY*100


if __name__ == "__main__":
    unittest.main()

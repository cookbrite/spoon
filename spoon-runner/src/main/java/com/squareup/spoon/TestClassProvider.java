/*
 * Copyright 2014 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.squareup.spoon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hands the next class to run to each given device.
 */
class TestClassProvider {
    private final List<TestClass> testClasses;

    public TestClassProvider(List<TestClass> testClasses) {
        this.testClasses = new ArrayList<TestClass>(testClasses);
        Collections.sort(testClasses);
    }

    /**
     * How many tests to run in total
     *
     * @return
     */
    public int size() {
        return testClasses.size();
    }

    /**
     * Returns the next class name available for running.
     * <p/>
     * The serial number isn't used yet, but may allow other pool members to pick up classes
     * pending on (possibly) wedged devices.
     *
     * @return
     */
    public synchronized TestClass getNextTest() {
        if (!testClasses.isEmpty()) {
            return testClasses.remove(0);
        }
        return null;
    }

    /**
     * return next class names available for running, in @count batches
     * @param count
     * @return null if no more tests available
     */
    public synchronized List<TestClass> getNextTests(int count) {
        List<TestClass> ret = new ArrayList<TestClass>(count);
        int i = 0;
        while (i < count && !testClasses.isEmpty()) {
            ret.add(testClasses.remove(0));
            i++;
        }
        return ret.size() > 0 ? ret : null;
    }

    public static String[] getTestClassNames(List<TestClass> in) {
        if (in == null || in.size() == 0) {
            return null;
        }

        String[] ret = new String[in.size()];
        for (int i = 0; i < in.size(); i++) {
            TestClass t = in.get(i);
            ret[i] = t.getClassName();
        }
        return ret;
    }
}

/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static com.github.benmanes.caffeine.cache.RandomSeedEnforcer.ensureRandomSeed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.ThreadLocalRandom;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class FrequencySketchTest {
  final int item = ThreadLocalRandom.current().nextInt();

  @Test
  public void construc() {
    FrequencySketch sketch = new FrequencySketch();
    assertThat(sketch.table, is(nullValue()));
  }

  @Test(dataProvider = "sketch", expectedExceptions = IllegalArgumentException.class)
  public void ensureCapacity_negative(FrequencySketch sketch) {
    sketch.ensureCapacity(-1);
  }

  @Test(dataProvider = "sketch")
  public void ensureCapacity_smaller(FrequencySketch sketch) {
    int size = sketch.table.length;
    sketch.ensureCapacity(size / 2);
    assertThat(sketch.table.length, is(size));
    assertThat(sketch.tableMask, is(size - 1));
    assertThat(sketch.sampleSize, is(10 * size));
  }

  @Test(dataProvider = "sketch")
  public void ensureCapacity_larger(FrequencySketch sketch) {
    int size = sketch.table.length;
    sketch.ensureCapacity(size * 2);
    assertThat(sketch.table.length, is(size * 2));
    assertThat(sketch.tableMask, is(2 * size - 1));
    assertThat(sketch.sampleSize, is(10 * 2 * size));
  }

  @Test(dataProvider = "sketch")
  public void increment_once(FrequencySketch sketch) {
    sketch.increment(item);
    assertThat(sketch.frequency(item), is(1));
  }

  @Test(dataProvider = "sketch")
  public void increment_max(FrequencySketch sketch) {
    for (int i = 0; i < 20; i++) {
      sketch.increment(item);
    }
    assertThat(sketch.frequency(item), is(15));
  }

  @Test(dataProvider = "sketch")
  public void increment_distinct(FrequencySketch sketch) {
    sketch.increment(item);
    sketch.increment(item + 1);
    assertThat(sketch.frequency(item), is(1));
    assertThat(sketch.frequency(item + 1), is(1));
    assertThat(sketch.frequency(item + 2), is(0));
  }

  @Test
  public void reset() {
    boolean reset = false;
    FrequencySketch sketch = new FrequencySketch();
    sketch.ensureCapacity(64);

    for (int i = 1; i < 20 * sketch.table.length; i++) {
      sketch.increment(i);
      if (sketch.size != i) {
        reset = true;
        break;
      }
    }
    assertThat(reset, is(true));
    assertThat(sketch.size, lessThanOrEqualTo(sketch.sampleSize / 2));
  }

  @Test
  public void heavyHitters() {
    FrequencySketch sketch = makeSketch(512);
    for (int i = 100; i < 100_000; i++) {
      sketch.increment(Double.hashCode(i));
    }
    for (int i = 0; i < 10; i += 2) {
      for (int j = 0; j < i; j++) {
        sketch.increment(Double.hashCode(i));
      }
    }

    // A perfect popularity count yields an array [0, 0, 2, 0, 4, 0, 6, 0, 8, 0]
    int[] popularity = new int[10];
    for (int i = 0; i < 10; i++) {
      popularity[i] = sketch.frequency(Double.hashCode(i));
    }
    for (int i = 0; i < popularity.length; i++) {
      if ((i == 0) || (i == 1) || (i == 3) || (i == 5) || (i == 7) || (i == 9)) {
        assertThat(popularity[i], lessThanOrEqualTo(popularity[2]));
      } else if (i == 2) {
        assertThat(popularity[2], lessThanOrEqualTo(popularity[4]));
      } else if (i == 4) {
        assertThat(popularity[4], lessThanOrEqualTo(popularity[6]));
      } else if (i == 6) {
        assertThat(popularity[6], lessThanOrEqualTo(popularity[8]));
      }
    }
  }

  @DataProvider(name = "sketch")
  public Object[][] providesSketch() {
    return new Object[][] {{ makeSketch(512) }};
  }

  private static FrequencySketch makeSketch(long maximumSize) {
    FrequencySketch sketch = new FrequencySketch();
    sketch.ensureCapacity(maximumSize);
    ensureRandomSeed(sketch);
    return sketch;
  }
}

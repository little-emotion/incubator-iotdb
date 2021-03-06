/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.tsfile.encoding.encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;

/**
 * Encoder for int value using gorilla encoding.
 */
public class SinglePrecisionEncoder extends GorillaEncoder {

  private int preValue;

  public SinglePrecisionEncoder() {
  }

  @Override
  public void encode(float value, ByteArrayOutputStream out) throws IOException {
    if (!flag) {
      flag = true;
      preValue = Float.floatToIntBits(value);
      leadingZeroNum = Integer.numberOfLeadingZeros(preValue);
      tailingZeroNum = Integer.numberOfTrailingZeros(preValue);
      out.write((preValue >> 0) & 0xFF);
      out.write((preValue >> 8) & 0xFF);
      out.write((preValue >> 16) & 0xFF);
      out.write((preValue >> 24) & 0xFF);
    } else {
      int nextValue = Float.floatToIntBits(value);
      int tmp = nextValue ^ preValue;
      if (tmp == 0) {
        // case: write '0'
        writeBit(false, out);
      } else {
        int leadingZeroNumTmp = Integer.numberOfLeadingZeros(tmp);
        int tailingZeroNumTmp = Integer.numberOfTrailingZeros(tmp);
        if (leadingZeroNumTmp >= leadingZeroNum && tailingZeroNumTmp >= tailingZeroNum) {
          // case: write '10' and effective bits without first leadingZeroNum '0' and
          // last tailingZeroNum '0'
          writeBit(true, out);
          writeBit(false, out);
          writeBits(tmp, out, TSFileConfig.FLOAT_LENGTH - 1 - leadingZeroNum,
              tailingZeroNum);
        } else {
          // case: write '11', leading zero num of value, effective bits len and effective
          // bit value
          writeBit(true, out);
          writeBit(true, out);
          writeBits(leadingZeroNumTmp, out,
              TSFileConfig.FLAOT_LEADING_ZERO_LENGTH - 1, 0);
          writeBits(TSFileConfig.FLOAT_LENGTH - leadingZeroNumTmp - tailingZeroNumTmp, out,
              TSFileConfig.FLOAT_VALUE_LENGTH - 1, 0);
          writeBits(tmp, out, TSFileConfig.FLOAT_LENGTH - 1 - leadingZeroNumTmp,
              tailingZeroNumTmp);
        }
      }
      preValue = nextValue;
      leadingZeroNum = Integer.numberOfLeadingZeros(preValue);
      tailingZeroNum = Integer.numberOfTrailingZeros(preValue);
    }
  }

  @Override
  public void flush(ByteArrayOutputStream out) throws IOException {
    encode(Float.NaN, out);
    clearBuffer(out);
    reset();
  }

  private void writeBits(int num, ByteArrayOutputStream out, int start, int end) {
    for (int i = start; i >= end; i--) {
      int bit = num & (1 << i);
      writeBit(bit, out);
    }
  }

  @Override
  public int getOneItemMaxSize() {
    // case '11'
    // 2bit + 5bit + 6bit + 32bit = 45bit
    return 6;
  }

  @Override
  public long getMaxByteSize() {
    // max(first 4 byte, case '11' bit + 5bit + 6bit + 32bit = 45bit) +
    // NaN(case '11' bit + 5bit + 6bit + 32bit = 45bit) = 90bit
    return 12;
  }
}

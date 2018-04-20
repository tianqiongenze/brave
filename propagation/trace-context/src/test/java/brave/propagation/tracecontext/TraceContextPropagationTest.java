/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.propagation.tracecontext;

import brave.propagation.Propagation.KeyFactory;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.tracecontext.TraceContextPropagation.Extra;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.internal.HexCodec.lowerHexToUnsignedLong;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextPropagationTest {
  Map<String, String> carrier = new LinkedHashMap<>();
  Injector<Map<String, String>> injector =
      TraceContextPropagation.FACTORY.create(KeyFactory.STRING).injector(Map::put);
  Extractor<Map<String, String>> extractor =
      TraceContextPropagation.FACTORY.create(KeyFactory.STRING).extractor(Map::get);

  TraceContext sampledContext =
      TraceContext.newBuilder()
          .traceIdHigh(lowerHexToUnsignedLong("67891233abcdef01"))
          .traceId(lowerHexToUnsignedLong("2345678912345678"))
          .spanId(lowerHexToUnsignedLong("463ac35c9f6413ad"))
          .sampled(true)
          .build();
  String validTraceparent = "00-67891233abcdef012345678912345678-463ac35c9f6413ad-01";
  String otherState = "congo=lZWRzIHRoNhcm5hbCBwbGVhc3VyZS4=";

  @Test public void injects_tc_when_no_other_tracestate() {
    Extra extra = new Extra();

    sampledContext = sampledContext.toBuilder().extra(asList(extra)).build();

    injector.inject(sampledContext, carrier);

    assertThat(carrier).containsEntry("tracestate", "tc=" + validTraceparent);
  }

  @Test public void injects_tc_before_other_tracestate() {
    Extra extra = new Extra();
    extra.otherState = otherState;

    sampledContext = sampledContext.toBuilder().extra(asList(extra)).build();

    injector.inject(sampledContext, carrier);

    assertThat(carrier).containsEntry("tracestate", "tc=" + validTraceparent + "," + otherState);
  }

  @Test public void extracts_tc_when_no_other_tracestate() {
    carrier.put("traceparent", validTraceparent);
    carrier.put("tracestate", "tc=" + validTraceparent);

    assertThat(extractor.extract(carrier))
        .isEqualTo(
            TraceContextOrSamplingFlags.newBuilder()
                .addExtra(new Extra())
                .context(sampledContext)
                .build());
  }

  @Test public void extracts_tc_before_other_tracestate() {
    carrier.put("traceparent", validTraceparent);
    carrier.put("tracestate", "tc=" + validTraceparent + "," + otherState);

    Extra extra = new Extra();
    extra.otherState = otherState;

    assertThat(extractor.extract(carrier))
        .isEqualTo(
            TraceContextOrSamplingFlags.newBuilder()
                .addExtra(extra)
                .context(sampledContext)
                .build());
  }

  @Test public void extracts_tc_after_other_tracestate() {
    carrier.put("traceparent", validTraceparent);
    carrier.put("tracestate", otherState + ",tc=" + validTraceparent);

    Extra extra = new Extra();
    extra.otherState = otherState;

    assertThat(extractor.extract(carrier))
        .isEqualTo(
            TraceContextOrSamplingFlags.newBuilder()
                .addExtra(extra)
                .context(sampledContext)
                .build());
  }
}
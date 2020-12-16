/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.loadbalancer.stats;

import java.util.function.Function;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import org.springframework.boot.actuate.metrics.http.Outcome;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.util.StringUtils;

/**
 * @author Olga Maciaszek-Sharma
 */
final class LoadBalancerTags {

	private static final String UNKNOWN = "UNKNOWN";

	private static final Pattern PATTERN_BEFORE_PATH = Pattern
			.compile("^https?://[^/]+/");

	private LoadBalancerTags() {
		throw new UnsupportedOperationException("Cannot instantiate utility class");
	}

	static Iterable<Tag> buildSuccessRequestTags(
			CompletionContext<Object, ServiceInstance, Object> completionContext) {
		ServiceInstance serviceInstance = completionContext.getLoadBalancerResponse()
				.getServer();
		Tags tags = Tags.of(buildServiceInstanceTags(serviceInstance));
		Object clientResponse = completionContext.getClientResponse();
		if (clientResponse instanceof ResponseData) {
			ResponseData responseData = (ResponseData) clientResponse;
			RequestData requestData = responseData.getRequestData();
			if (requestData != null) {
				tags = tags.and(valueOrUnknown("method", requestData.getHttpMethod()),
						valueOrUnknown("uri", getPath(requestData)));
			}
			else {
				tags = tags.and(Tag.of("method", UNKNOWN), Tag.of("uri", UNKNOWN));
			}

			tags = tags
					.and(Outcome.forStatus(responseData.getHttpStatus().value()).asTag(),
							valueOrUnknown("status", responseData.getHttpStatus()));
		}
		else {
			tags = tags.and(Tag.of("method", UNKNOWN), Tag.of("uri", UNKNOWN),
					Tag.of("outcome", UNKNOWN), Tag.of("status", UNKNOWN));
		}
		return tags;
	}

	private static String getPath(RequestData requestData) {
		return requestData.getUrl() != null ? requestData.getUrl().getPath() : UNKNOWN;
	}

	static Iterable<Tag> buildDiscardedRequestTags(CompletionContext<Object, ServiceInstance, Object> completionContext) {
		if (completionContext.getLoadBalancerRequest()
				.getContext() instanceof RequestDataContext) {
			RequestData requestData = ((RequestDataContext) completionContext
					.getLoadBalancerRequest().getContext()).getClientRequest();
			if (requestData != null) {
				return Tags.of(valueOrUnknown("method", requestData.getHttpMethod()),
						valueOrUnknown("uri", getPath(requestData)),
						valueOrUnknown("serviceId", getHost(requestData)));
			}
		}
		return Tags.of(valueOrUnknown("method", UNKNOWN),
				valueOrUnknown("uri", UNKNOWN),
				valueOrUnknown("serviceId", UNKNOWN));

	}

	private static String getHost(RequestData requestData) {
		return requestData.getUrl() != null ? requestData.getUrl().getHost() : UNKNOWN;
	}

	static Iterable<Tag> buildFailedRequestTags(CompletionContext<Object, ServiceInstance, Object> completionContext) {
		ServiceInstance serviceInstance = completionContext.getLoadBalancerResponse()
				.getServer();
		Tags tags = Tags.of(buildServiceInstanceTags(serviceInstance))
				.and(exception(completionContext.getThrowable()));
		if (completionContext.getLoadBalancerRequest()
				.getContext() instanceof RequestDataContext) {
			RequestData requestData = ((RequestDataContext) completionContext
					.getLoadBalancerRequest().getContext()).getClientRequest();
			if (requestData != null) {
				return tags.and(Tags
						.of(valueOrUnknown("method", requestData.getHttpMethod()),
								valueOrUnknown("uri", getPath(requestData))));
			}
		}
		return tags.and(Tags.of(valueOrUnknown("method", UNKNOWN),
				valueOrUnknown("uri", UNKNOWN)));
	}


	static Iterable<Tag> buildServiceInstanceTags(ServiceInstance serviceInstance) {
		return Tags.of(valueOrUnknown("serviceId", serviceInstance.getServiceId()),
				valueOrUnknown("serviceInstance.instanceId", serviceInstance
						.getInstanceId()),
				valueOrUnknown("serviceInstance.host", serviceInstance.getHost()),
				valueOrUnknown("serviceInstance.port", String
						.valueOf(serviceInstance.getPort())),
				valueOrUnknown("serviceInstance.uri", serviceInstance
						.getUri(), extractPath()),
				valueOrUnknown("serviceInstance.secure", String
						.valueOf(serviceInstance.isSecure())));
	}

	private static Tag valueOrUnknown(String key, String value) {
		if (value != null) {
			return Tag.of(key, value);
		}
		return Tag.of(key, UNKNOWN);
	}

	private static Tag valueOrUnknown(String key, Object value) {
		if (value != null) {
			return Tag.of(key, String.valueOf(value));
		}
		return Tag.of(key, UNKNOWN);
	}

	private static Tag valueOrUnknown(String key, Object value, Function<String, String> supplier) {
		if (value != null) {
			return Tag.of(key, supplier.apply(value.toString()));
		}
		return Tag.of(key, UNKNOWN);
	}

	private static Function<String, String> extractPath() {
		return url -> {
			String path = PATTERN_BEFORE_PATH.matcher(url).replaceFirst("");
			return (path.startsWith("/") ? path : "/" + path);
		};
	}

	private static Tag exception(Throwable exception) {
		if (exception != null) {
			String simpleName = exception.getClass().getSimpleName();
			return Tag.of("exception", StringUtils
					.hasText(simpleName) ? simpleName : exception.getClass().getName());
		}
		return Tag.of("exception", "None");
	}

}

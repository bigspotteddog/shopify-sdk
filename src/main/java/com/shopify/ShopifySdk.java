package com.shopify;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.shopify.exceptions.ShopifyClientException;
import com.shopify.exceptions.ShopifyErrorResponseException;
import com.shopify.model.Count;
import com.shopify.model.Image;
import com.shopify.model.ImageAltTextCreationRequest;
import com.shopify.model.Metafield;
import com.shopify.model.MetafieldRoot;
import com.shopify.model.MetafieldsRoot;
import com.shopify.model.Shop;
import com.shopify.model.ShopifyAccessTokenRoot;
import com.shopify.model.ShopifyCancelOrderRequest;
import com.shopify.model.ShopifyFulfillment;
import com.shopify.model.ShopifyFulfillmentCreationRequest;
import com.shopify.model.ShopifyFulfillmentRoot;
import com.shopify.model.ShopifyFulfillmentUpdateRequest;
import com.shopify.model.ShopifyGiftCard;
import com.shopify.model.ShopifyGiftCardCreationRequest;
import com.shopify.model.ShopifyGiftCardRoot;
import com.shopify.model.ShopifyImageRoot;
import com.shopify.model.ShopifyInventoryLevel;
import com.shopify.model.ShopifyInventoryLevelRoot;
import com.shopify.model.ShopifyLocation;
import com.shopify.model.ShopifyLocationsRoot;
import com.shopify.model.ShopifyOrder;
import com.shopify.model.ShopifyOrderCreationRequest;
import com.shopify.model.ShopifyOrderRisk;
import com.shopify.model.ShopifyOrderRisksRoot;
import com.shopify.model.ShopifyOrderRoot;
import com.shopify.model.ShopifyOrdersRoot;
import com.shopify.model.ShopifyProduct;
import com.shopify.model.ShopifyProductCreationRequest;
import com.shopify.model.ShopifyProductMetafieldCreationRequest;
import com.shopify.model.ShopifyProductRequest;
import com.shopify.model.ShopifyProductRoot;
import com.shopify.model.ShopifyProductUpdateRequest;
import com.shopify.model.ShopifyProducts;
import com.shopify.model.ShopifyProductsRoot;
import com.shopify.model.ShopifyRecurringApplicationCharge;
import com.shopify.model.ShopifyRecurringApplicationChargeCreationRequest;
import com.shopify.model.ShopifyRecurringApplicationChargeRoot;
import com.shopify.model.ShopifyRefund;
import com.shopify.model.ShopifyRefundCreationRequest;
import com.shopify.model.ShopifyRefundRoot;
import com.shopify.model.ShopifyShop;
import com.shopify.model.ShopifyTransaction;
import com.shopify.model.ShopifyTransactionsRoot;
import com.shopify.model.ShopifyVariant;
import com.shopify.model.ShopifyVariantMetafieldCreationRequest;
import com.shopify.model.ShopifyVariantRoot;
import com.shopify.model.ShopifyVariantUpdateRequest;

public class ShopifySdk {

	private static final Logger LOGGER = LoggerFactory.getLogger(ShopifySdk.class);

	private static final String HTTPS = "https://";
	private static final String API_TARGET = ".myshopify.com/admin";
	static final String ACCESS_TOKEN_HEADER = "X-Shopify-Access-Token";
	static final String DEPRECATED_REASON_HEADER = "X-Shopify-API-Deprecated-Reason";
	static final String OAUTH = "oauth";
	static final String REVOKE = "revoke";
	static final String ACCESS_TOKEN = "access_token";
	static final String PRODUCTS = "products";
	static final String VARIANTS = "variants";
	static final String RECURRING_APPLICATION_CHARGES = "recurring_application_charges";
	static final String ORDERS = "orders";
	static final String FULFILLMENTS = "fulfillments";
	static final String ACTIVATE = "activate";
	static final String IMAGES = "images";
	static final String SHOP = "shop";
	static final String COUNT = "count";
	static final String CLOSE = "close";
	static final String CANCEL = "cancel";
	static final String METAFIELDS = "metafields";
	static final String RISKS = "risks";
	static final String LOCATIONS = "locations";
	static final String INVENTORY_LEVELS = "inventory_levels";
	static final String JSON = ".json";
	static final String LIMIT_QUERY_PARAMETER = "limit";
	static final String PAGE_QUERY_PARAMETER = "page";
	static final String STATUS_QUERY_PARAMETER = "status";
	static final String ANY_STATUSES = "any";
	static final String CREATED_AT_MIN_QUERY_PARAMETER = "created_at_min";
	static final String CREATED_AT_MAX_QUERY_PARAMETER = "created_at_max";
	static final String ATTRIBUTION_APP_ID_QUERY_PARAMETER = "attribution_app_id";
	static final String CALCULATE = "calculate";
	static final String REFUNDS = "refunds";
	static final String TRANSACTIONS = "transactions";
	static final String GIFT_CARDS = "gift_cards";
	static final String REFUND_KIND = "refund";
	static final String SET = "set";
	private static final String CLIENT_ID = "client_id";
	private static final String CLIENT_SECRET = "client_secret";
	private static final String AUTHORIZATION_CODE = "code";

	private static final int DEFAULT_REQUEST_LIMIT = 50;
	private static final int TOO_MANY_REQUESTS_STATUS_CODE = 429;
	private static final int UNPROCESSABLE_ENTITY_STATUS_CODE = 422;
	private static final int LOCKED_STATUS_CODE = 423;

	private static final String SHOP_RETRIEVED_MESSAGE = "Starting to make calls for Shopify store with ID of {} and name of {}";
	private static final String COULD_NOT_BE_SAVED_SHOPIFY_ERROR_MESSAGE = "could not successfully be saved";
	private static final String RETRY_ATTEMPT_MESSAGE = "Waited {} seconds since first retry attempt. This is attempt {}. Please review the following failed request information.\nRequest Location of {}\nResponse Status Code of {}\nResponse Headers of:\n{}\nResponse Body of:\n{}";
	private static final String RETRY_FAILED_MESSAGE = "Request retry has failed.";
	private static final String DEPRECATED_SHOPIFY_CALL_ERROR_MESSAGE = "Shopify call is deprecated. Please take note of the X-Shopify-API-Deprecated-Reason and correct the call.\nRequest Location of {}\nResponse Status Code of {}\nResponse Headers of:\n{}";
	static final String GENERAL_ACCESS_TOKEN_EXCEPTION_MESSAGE = "There was a problem generating access token using shop subdomain of %s and authorization code of %s.";

	private static final int ONE_MINUTE_IN_MILLISECONDS = 60000;
	private static final int FIVE_MINUTES_IN_MILLISECONDS = 300000;

	private String shopSubdomain;
	private String apiUrl;
	private String clientId;
	private String clientSecret;
	private String authorizationToken;
	private WebTarget webTarget;
	private String accessToken;

	private static final Client CLIENT = buildClient();

	public static interface BuildStep {
		ShopifySdk build();
	}

	public static interface AuthorizationTokenStep {
		BuildStep withAuthorizationToken(final String authorizationToken);

	}

	public static interface ClientSecretStep {
		AuthorizationTokenStep withClientSecret(final String clientSecret);

	}

	public static interface AccessTokenStep {
		BuildStep withAccessToken(final String accessToken);

		ClientSecretStep withClientId(final String clientId);
	}

	public static interface SubdomainStep {
		AccessTokenStep withSubdomain(final String subdomain);

		AccessTokenStep withApiUrl(final String apiUrl);
	}

	public static SubdomainStep newBuilder() {
		return new Steps();
	}

	protected ShopifySdk(final Steps steps) {
		if (steps != null) {
			this.shopSubdomain = steps.subdomain;
			this.accessToken = steps.accessToken;
			this.clientId = steps.clientId;
			this.clientSecret = steps.clientSecret;
			this.authorizationToken = steps.authorizationToken;
			this.apiUrl = steps.apiUrl;
		}

	}

	protected static class Steps
			implements SubdomainStep, ClientSecretStep, AuthorizationTokenStep, AccessTokenStep, BuildStep {
		private String subdomain;
		private String accessToken;
		private String clientId;
		private String clientSecret;
		private String authorizationToken;
		private String apiUrl;

		@Override
		public ShopifySdk build() {
			return new ShopifySdk(this);
		}

		@Override
		public BuildStep withAccessToken(final String accessToken) {
			this.accessToken = accessToken;
			return this;
		}

		@Override
		public AccessTokenStep withSubdomain(final String subdomain) {
			this.subdomain = subdomain;
			return this;
		}

		@Override
		public AccessTokenStep withApiUrl(final String apiUrl) {
			this.apiUrl = apiUrl;
			return this;
		}

		@Override
		public ClientSecretStep withClientId(final String clientId) {
			this.clientId = clientId;
			return this;
		}

		@Override
		public BuildStep withAuthorizationToken(final String authorizationToken) {
			this.authorizationToken = authorizationToken;
			return this;
		}

		@Override
		public AuthorizationTokenStep withClientSecret(final String clientSecret) {
			this.clientSecret = clientSecret;
			return this;
		}

	}

	public boolean revokeOAuthToken() {
		try {
			final Response response = delete(getWebTarget().path(OAUTH).path(REVOKE));
			return Status.OK.getStatusCode() == response.getStatus();
		} catch (final ShopifyErrorResponseException e) {
			return false;
		}
	}

	public ShopifyProduct getProduct(final String productId) {
		final Response response = get(getWebTarget().path(PRODUCTS).path(productId));
		final ShopifyProductRoot shopifyProductRootResponse = response.readEntity(ShopifyProductRoot.class);
		return shopifyProductRootResponse.getProduct();
	}

	public ShopifyVariant getVariant(final String variantId) {
		final Response response = get(getWebTarget().path(VARIANTS).path(variantId));
		final ShopifyVariantRoot shopifyVariantRootResponse = response.readEntity(ShopifyVariantRoot.class);
		return shopifyVariantRootResponse.getVariant();
	}

	public List<ShopifyProduct> getProducts(final int page, final int pageSize) {
		final Response response = get(getWebTarget().path(PRODUCTS).queryParam(LIMIT_QUERY_PARAMETER, pageSize)
				.queryParam(PAGE_QUERY_PARAMETER, page));
		final ShopifyProductsRoot shopifyProductsRoot = response.readEntity(ShopifyProductsRoot.class);
		return shopifyProductsRoot.getProducts();
	}

	public ShopifyProducts getProducts() {
		final List<ShopifyProduct> shopifyProducts = new LinkedList<>();

		List<ShopifyProduct> shopifyProductsPage;
		int page = 1;
		do {
			shopifyProductsPage = getProducts(page, DEFAULT_REQUEST_LIMIT);
			LOGGER.info("Retrieved {} products from page {}", shopifyProductsPage.size(), page);
			page++;
			shopifyProducts.addAll(shopifyProductsPage);
		} while (!shopifyProductsPage.isEmpty());

		return new ShopifyProducts(shopifyProducts);
	}

	public int getProductCount() {
		final Response response = get(getWebTarget().path(PRODUCTS).path(COUNT));
		final Count count = response.readEntity(Count.class);
		return count.getCount();
	}

	public ShopifyShop getShop() {
		final Response response = get(getWebTarget().path(SHOP));
		return response.readEntity(ShopifyShop.class);
	}

	public ShopifyProduct createProduct(final ShopifyProductCreationRequest shopifyProductCreationRequest) {
		final ShopifyProductRoot shopifyProductRootRequest = new ShopifyProductRoot();
		final ShopifyProduct shopifyProduct = shopifyProductCreationRequest.getRequest();
		shopifyProductRootRequest.setProduct(shopifyProduct);
		final Response response = post(getWebTarget().path(PRODUCTS), shopifyProductRootRequest);
		final ShopifyProductRoot shopifyProductRootResponse = response.readEntity(ShopifyProductRoot.class);
		final ShopifyProduct createdShopifyProduct = shopifyProductRootResponse.getProduct();
		return updateProductImages(shopifyProductCreationRequest, createdShopifyProduct);
	}

	public ShopifyProduct updateProduct(final ShopifyProductUpdateRequest shopifyProductUpdateRequest) {
		final ShopifyProductRoot shopifyProductRootRequest = new ShopifyProductRoot();
		final ShopifyProduct shopifyProduct = shopifyProductUpdateRequest.getRequest();
		shopifyProductRootRequest.setProduct(shopifyProduct);
		final Response response = put(getWebTarget().path(PRODUCTS).path(shopifyProduct.getId()),
				shopifyProductRootRequest);
		final ShopifyProductRoot shopifyProductRootResponse = response.readEntity(ShopifyProductRoot.class);
		final ShopifyProduct updatedShopifyProduct = shopifyProductRootResponse.getProduct();
		return updateProductImages(shopifyProductUpdateRequest, updatedShopifyProduct);
	}

	public ShopifyVariant updateVariant(final ShopifyVariantUpdateRequest shopifyVariantUpdateRequest) {
		final ShopifyVariant shopifyVariant = shopifyVariantUpdateRequest.getRequest();
		final String shopifyVariantId = shopifyVariant.getId();
		if (StringUtils.isNotBlank(shopifyVariantUpdateRequest.getImageSource())) {
			final ShopifyImageRoot shopifyImageRootRequest = new ShopifyImageRoot();
			final Image imageRequest = new Image();
			imageRequest.setSource(shopifyVariantUpdateRequest.getImageSource());
			final List<Metafield> metafields = ImageAltTextCreationRequest.newBuilder()
					.withImageAltText(shopifyVariant.getTitle()).build();
			imageRequest.setMetafields(metafields);
			imageRequest.setVariantIds(Arrays.asList(shopifyVariantId));
			shopifyImageRootRequest.setImage(imageRequest);
			final String productId = shopifyVariant.getProductId();
			final Response response = post(getWebTarget().path(PRODUCTS).path(productId).path(IMAGES),
					shopifyImageRootRequest);
			final ShopifyImageRoot shopifyImageRootResponse = response.readEntity(ShopifyImageRoot.class);
			final Image createdImage = shopifyImageRootResponse.getImage();
			shopifyVariant.setImageId(createdImage.getId());
		}

		final ShopifyVariantRoot shopifyVariantRootRequest = new ShopifyVariantRoot();
		shopifyVariantRootRequest.setVariant(shopifyVariant);
		final Response response = put(getWebTarget().path(VARIANTS).path(shopifyVariantId), shopifyVariantRootRequest);
		final ShopifyVariantRoot shopifyVariantRootResponse = response.readEntity(ShopifyVariantRoot.class);
		return shopifyVariantRootResponse.getVariant();
	}

	public boolean deleteProduct(final String productId) {
		final Response response = delete(getWebTarget().path(PRODUCTS).path(productId));
		return Status.OK.getStatusCode() == response.getStatus();
	}

	public ShopifyRecurringApplicationCharge createRecurringApplicationCharge(
			final ShopifyRecurringApplicationChargeCreationRequest shopifyRecurringApplicationChargeCreationRequest) {
		final ShopifyRecurringApplicationChargeRoot shopifyRecurringApplicationChargeRootRequest = new ShopifyRecurringApplicationChargeRoot();
		final ShopifyRecurringApplicationCharge shopifyRecurringApplicationChargeRequest = shopifyRecurringApplicationChargeCreationRequest
				.getRequest();
		shopifyRecurringApplicationChargeRootRequest
				.setRecurringApplicationCharge(shopifyRecurringApplicationChargeRequest);

		final Response response = post(getWebTarget().path(RECURRING_APPLICATION_CHARGES),
				shopifyRecurringApplicationChargeRootRequest);
		final ShopifyRecurringApplicationChargeRoot shopifyRecurringApplicationChargeRootResponse = response
				.readEntity(ShopifyRecurringApplicationChargeRoot.class);
		return shopifyRecurringApplicationChargeRootResponse.getRecurringApplicationCharge();
	}

	public ShopifyRecurringApplicationCharge getRecurringApplicationCharge(final String chargeId) {
		final Response response = get(getWebTarget().path(RECURRING_APPLICATION_CHARGES).path(chargeId));
		final ShopifyRecurringApplicationChargeRoot shopifyRecurringApplicationChargeRootResponse = response
				.readEntity(ShopifyRecurringApplicationChargeRoot.class);
		return shopifyRecurringApplicationChargeRootResponse.getRecurringApplicationCharge();
	}

	public ShopifyRecurringApplicationCharge activateRecurringApplicationCharge(final String chargeId) {
		final ShopifyRecurringApplicationCharge shopifyRecurringApplicationChargeRequest = getRecurringApplicationCharge(
				chargeId);
		final Response response = post(getWebTarget().path(RECURRING_APPLICATION_CHARGES).path(chargeId).path(ACTIVATE),
				shopifyRecurringApplicationChargeRequest);
		final ShopifyRecurringApplicationChargeRoot shopifyRecurringApplicationChargeRootResponse = response
				.readEntity(ShopifyRecurringApplicationChargeRoot.class);
		return shopifyRecurringApplicationChargeRootResponse.getRecurringApplicationCharge();
	}

	public ShopifyOrder getOrder(final String orderId) {
		final Response response = get(getWebTarget().path(ORDERS).path(orderId));
		final ShopifyOrderRoot shopifyOrderRootResponse = response.readEntity(ShopifyOrderRoot.class);
		return shopifyOrderRootResponse.getOrder();
	}

	public List<ShopifyTransaction> getOrderTransactions(final String orderId) {
		final Response response = get(getWebTarget().path(ORDERS).path(orderId).path(TRANSACTIONS));

		final ShopifyTransactionsRoot shopifyTransactionsRootResponse = response
				.readEntity(ShopifyTransactionsRoot.class);
		return shopifyTransactionsRootResponse.getTransactions();
	}

	public List<ShopifyOrder> getOrders(final int page) {
		return getOrders(page, DEFAULT_REQUEST_LIMIT);
	}

	public List<ShopifyOrder> getOrders(final int page, final int pageSize) {
		final Response response = get(getWebTarget().path(ORDERS).queryParam(STATUS_QUERY_PARAMETER, ANY_STATUSES)
				.queryParam(LIMIT_QUERY_PARAMETER, pageSize).queryParam(PAGE_QUERY_PARAMETER, page));
		return getOrders(response);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final int page) {
		return getOrders(mininumCreationDate, page, DEFAULT_REQUEST_LIMIT);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final int page, final int pageSize) {
		final Response response = get(getWebTarget().path(ORDERS).queryParam(STATUS_QUERY_PARAMETER, ANY_STATUSES)
				.queryParam(LIMIT_QUERY_PARAMETER, pageSize)
				.queryParam(CREATED_AT_MIN_QUERY_PARAMETER, mininumCreationDate.toString())
				.queryParam(PAGE_QUERY_PARAMETER, page));
		return getOrders(response);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final DateTime maximumCreationDate,
			final int page) {
		return getOrders(mininumCreationDate, maximumCreationDate, page, DEFAULT_REQUEST_LIMIT);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final DateTime maximumCreationDate,
			final int page, final int pageSize) {
		final Response response = get(getWebTarget().path(ORDERS).queryParam(STATUS_QUERY_PARAMETER, ANY_STATUSES)
				.queryParam(LIMIT_QUERY_PARAMETER, pageSize)
				.queryParam(CREATED_AT_MIN_QUERY_PARAMETER, mininumCreationDate.toString())
				.queryParam(CREATED_AT_MAX_QUERY_PARAMETER, maximumCreationDate.toString())
				.queryParam(PAGE_QUERY_PARAMETER, page));
		return getOrders(response);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final DateTime maximumCreationDate,
			final int page, final String appId) {
		return getOrders(mininumCreationDate, maximumCreationDate, page, appId, DEFAULT_REQUEST_LIMIT);
	}

	public List<ShopifyOrder> getOrders(final DateTime mininumCreationDate, final DateTime maximumCreationDate,
			final int page, final String appId, final int pageSize) {
		final Response response = get(getWebTarget().path(ORDERS).queryParam(STATUS_QUERY_PARAMETER, ANY_STATUSES)
				.queryParam(LIMIT_QUERY_PARAMETER, pageSize)
				.queryParam(CREATED_AT_MIN_QUERY_PARAMETER, mininumCreationDate.toString())
				.queryParam(CREATED_AT_MAX_QUERY_PARAMETER, maximumCreationDate.toString())
				.queryParam(ATTRIBUTION_APP_ID_QUERY_PARAMETER, appId).queryParam(PAGE_QUERY_PARAMETER, page));
		return getOrders(response);
	}

	public ShopifyFulfillment createFulfillment(
			final ShopifyFulfillmentCreationRequest shopifyFulfillmentCreationRequest) {
		final ShopifyFulfillmentRoot shopifyFulfillmentRoot = new ShopifyFulfillmentRoot();
		final ShopifyFulfillment shopifyFulfillment = shopifyFulfillmentCreationRequest.getRequest();
		shopifyFulfillmentRoot.setFulfillment(shopifyFulfillment);
		final Response response = post(
				getWebTarget().path(ORDERS).path(shopifyFulfillment.getOrderId()).path(FULFILLMENTS),
				shopifyFulfillmentRoot);
		final ShopifyFulfillmentRoot shopifyFulfillmentRootResponse = response.readEntity(ShopifyFulfillmentRoot.class);
		return shopifyFulfillmentRootResponse.getFulfillment();
	}

	public ShopifyFulfillment updateFulfillment(final ShopifyFulfillmentUpdateRequest shopifyFulfillmentUpdateRequest) {
		final ShopifyFulfillmentRoot shopifyFulfillmentRoot = new ShopifyFulfillmentRoot();
		final ShopifyFulfillment shopifyFulfillment = shopifyFulfillmentUpdateRequest.getRequest();
		shopifyFulfillmentRoot.setFulfillment(shopifyFulfillment);
		final Response response = put(getWebTarget().path(ORDERS).path(shopifyFulfillment.getOrderId())
				.path(FULFILLMENTS).path(shopifyFulfillment.getId()), shopifyFulfillmentRoot);
		final ShopifyFulfillmentRoot shopifyFulfillmentRootResponse = response.readEntity(ShopifyFulfillmentRoot.class);
		return shopifyFulfillmentRootResponse.getFulfillment();
	}

	public ShopifyOrder createOrder(final ShopifyOrderCreationRequest shopifyOrderCreationRequest) {
		final ShopifyOrderRoot shopifyOrderRoot = new ShopifyOrderRoot();
		final ShopifyOrder shopifyOrder = shopifyOrderCreationRequest.getRequest();
		shopifyOrderRoot.setOrder(shopifyOrder);
		final Response response = post(getWebTarget().path(ORDERS), shopifyOrderRoot);
		final ShopifyOrderRoot shopifyOrderRootResponse = response.readEntity(ShopifyOrderRoot.class);
		return shopifyOrderRootResponse.getOrder();
	}

	public ShopifyFulfillment cancelFulfillment(final String orderId, final String fulfillmentId) {
		final Response response = post(
				getWebTarget().path(ORDERS).path(orderId).path(FULFILLMENTS).path(fulfillmentId).path(CANCEL),
				new ShopifyFulfillment());
		final ShopifyFulfillmentRoot shopifyFulfillmentRootResponse = response.readEntity(ShopifyFulfillmentRoot.class);
		return shopifyFulfillmentRootResponse.getFulfillment();
	}

	public ShopifyOrder closeOrder(final String orderId) {
		final Response response = post(getWebTarget().path(ORDERS).path(orderId).path(CLOSE), new ShopifyOrder());
		final ShopifyOrderRoot shopifyOrderRootResponse = response.readEntity(ShopifyOrderRoot.class);
		return shopifyOrderRootResponse.getOrder();
	}

	public ShopifyOrder cancelOrder(final String orderId, final String reason) {
		final ShopifyCancelOrderRequest shopifyCancelOrderRequest = new ShopifyCancelOrderRequest();
		shopifyCancelOrderRequest.setReason(reason);
		final Response response = post(getWebTarget().path(ORDERS).path(orderId).path(CANCEL),
				shopifyCancelOrderRequest);
		final ShopifyOrderRoot shopifyOrderRootResponse = response.readEntity(ShopifyOrderRoot.class);
		return shopifyOrderRootResponse.getOrder();
	}

	public Metafield createVariantMetafield(
			final ShopifyVariantMetafieldCreationRequest shopifyVariantMetafieldCreationRequest) {
		final MetafieldRoot metafieldRoot = new MetafieldRoot();
		metafieldRoot.setMetafield(shopifyVariantMetafieldCreationRequest.getRequest());
		final Response response = post(getWebTarget().path(VARIANTS)
				.path(shopifyVariantMetafieldCreationRequest.getVariantId()).path(METAFIELDS), metafieldRoot);
		final MetafieldRoot metafieldRootResponse = response.readEntity(MetafieldRoot.class);
		return metafieldRootResponse.getMetafield();
	}

	public List<Metafield> getVariantMetafields(final String variantId) {
		final Response response = get(getWebTarget().path(VARIANTS).path(variantId).path(METAFIELDS));
		final MetafieldsRoot metafieldsRootResponse = response.readEntity(MetafieldsRoot.class);
		return metafieldsRootResponse.getMetafields();
	}

	public Metafield createProductMetafield(
			final ShopifyProductMetafieldCreationRequest shopifyProductMetafieldCreationRequest) {
		final MetafieldRoot metafieldRoot = new MetafieldRoot();
		metafieldRoot.setMetafield(shopifyProductMetafieldCreationRequest.getRequest());
		final Response response = post(getWebTarget().path(PRODUCTS)
				.path(shopifyProductMetafieldCreationRequest.getProductId()).path(METAFIELDS), metafieldRoot);
		final MetafieldRoot metafieldRootResponse = response.readEntity(MetafieldRoot.class);
		return metafieldRootResponse.getMetafield();
	}

	public List<Metafield> getProductMetafields(final String productId) {
		final Response response = get(getWebTarget().path(PRODUCTS).path(productId).path(METAFIELDS));
		final MetafieldsRoot metafieldsRootResponse = response.readEntity(MetafieldsRoot.class);
		return metafieldsRootResponse.getMetafields();
	}

	public List<ShopifyOrderRisk> getOrderRisks(final String orderId) {
		final Response response = get(getWebTarget().path(ORDERS).path(orderId).path(RISKS));
		final ShopifyOrderRisksRoot shopifyOrderRisksRootResponse = response.readEntity(ShopifyOrderRisksRoot.class);
		return shopifyOrderRisksRootResponse.getRisks();
	}

	public List<ShopifyLocation> getLocations() {
		final String locationsEndpoint = new StringBuilder().append(LOCATIONS).append(JSON).toString();
		final Response response = get(getWebTarget().path(locationsEndpoint));
		final ShopifyLocationsRoot shopifyLocationRootResponse = response.readEntity(ShopifyLocationsRoot.class);
		return shopifyLocationRootResponse.getLocations();
	}

	public ShopifyInventoryLevel updateInventoryLevel(final String inventoryItemId, final String locationId,
			final long quantity) {
		final ShopifyInventoryLevel shopifyInventoryLevel = new ShopifyInventoryLevel();
		shopifyInventoryLevel.setAvailable(quantity);
		shopifyInventoryLevel.setLocationId(locationId);
		shopifyInventoryLevel.setInventoryItemId(inventoryItemId);
		final Response response = post(getWebTarget().path(INVENTORY_LEVELS).path(SET), shopifyInventoryLevel);
		final ShopifyInventoryLevelRoot shopifyInventoryLevelRootResponse = response
				.readEntity(ShopifyInventoryLevelRoot.class);
		return shopifyInventoryLevelRootResponse.getInventoryLevel();
	}

	public List<Metafield> getOrderMetafields(final String orderId) {
		final Response response = get(getWebTarget().path(ORDERS).path(orderId).path(METAFIELDS));
		final MetafieldsRoot metafieldsRootResponse = response.readEntity(MetafieldsRoot.class);
		return metafieldsRootResponse.getMetafields();
	}

	public ShopifyRefund refund(final ShopifyRefundCreationRequest shopifyRefundCreationRequest) {

		final ShopifyRefund calculatedShopifyRefund = calculateRefund(shopifyRefundCreationRequest);
		calculatedShopifyRefund.getTransactions().forEach(transaction -> transaction.setKind(REFUND_KIND));

		final WebTarget path = getWebTarget().path(ORDERS).path(shopifyRefundCreationRequest.getRequest().getOrderId())
				.path(REFUNDS);
		final ShopifyRefundRoot shopifyRefundRoot = new ShopifyRefundRoot();
		shopifyRefundRoot.setRefund(calculatedShopifyRefund);
		final Response response = post(path, shopifyRefundRoot);
		final ShopifyRefundRoot shopifyRefundRootResponse = response.readEntity(ShopifyRefundRoot.class);
		return shopifyRefundRootResponse.getRefund();

	}

	public ShopifyGiftCard createGiftCard(final ShopifyGiftCardCreationRequest shopifyGiftCardCreationRequest) {
		final ShopifyGiftCardRoot shopifyGiftCardRoot = new ShopifyGiftCardRoot();
		final ShopifyGiftCard shopifyGiftCard = shopifyGiftCardCreationRequest.getRequest();
		shopifyGiftCardRoot.setGiftCard(shopifyGiftCard);
		final Response response = post(getWebTarget().path(GIFT_CARDS), shopifyGiftCardRoot);
		final ShopifyGiftCardRoot shopifyOrderRootResponse = response.readEntity(ShopifyGiftCardRoot.class);
		return shopifyOrderRootResponse.getGiftCard();
	}

	public String getAccessToken() {
		return accessToken;
	}

	private ShopifyRefund calculateRefund(final ShopifyRefundCreationRequest shopifyRefundCreationRequest) {
		final ShopifyRefundRoot shopifyRefundRoot = new ShopifyRefundRoot();

		shopifyRefundRoot.setRefund(shopifyRefundCreationRequest.getRequest());

		final WebTarget path = getWebTarget().path(ORDERS).path(shopifyRefundCreationRequest.getRequest().getOrderId())
				.path(REFUNDS).path(CALCULATE);
		final Response response = post(path, shopifyRefundRoot);
		final ShopifyRefundRoot shopifyRefundRootResponse = response.readEntity(ShopifyRefundRoot.class);
		return shopifyRefundRootResponse.getRefund();

	}

	private ShopifyProduct updateProductImages(final ShopifyProductRequest shopifyProductRequest,
			final ShopifyProduct shopifyProduct) {
		setVariantImageIds(shopifyProductRequest, shopifyProduct);
		final ShopifyProductRoot shopifyProductRootRequest = new ShopifyProductRoot();
		shopifyProductRootRequest.setProduct(shopifyProduct);
		final Response response = put(getWebTarget().path(PRODUCTS).path(shopifyProduct.getId()),
				shopifyProductRootRequest);
		final ShopifyProductRoot shopifyProductRootResponse = response.readEntity(ShopifyProductRoot.class);
		return shopifyProductRootResponse.getProduct();
	}

	private void setVariantImageIds(final ShopifyProductRequest shopifyProductRequest,
			final ShopifyProduct shopifyProduct) {
		shopifyProduct.getVariants().stream().forEach(variant -> {
			final int variantPosition = variant.getPosition();
			if (shopifyProductRequest.hasVariantImagePosition(variantPosition)) {
				final int imagePosition = shopifyProductRequest.getVariantImagePosition(variantPosition);
				shopifyProduct.getImages().stream().filter(image -> image.getPosition() == imagePosition).findFirst()
						.ifPresent(variantImage -> variant.setImageId(variantImage.getId()));
			}
		});
	}

	private List<ShopifyOrder> getOrders(final Response response) {
		final ShopifyOrdersRoot shopifyOrderRootResponse = response.readEntity(ShopifyOrdersRoot.class);
		return shopifyOrderRootResponse.getOrders();
	}

	private Response get(final WebTarget webTarget) {
		final Callable<Response> responseCallable = () -> webTarget.request(MediaType.APPLICATION_JSON)
				.header(ACCESS_TOKEN_HEADER, accessToken).get();
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, Status.OK);
	}

	private Response delete(final WebTarget webTarget) {
		final Callable<Response> responseCallable = () -> webTarget.request(MediaType.APPLICATION_JSON)
				.header(ACCESS_TOKEN_HEADER, accessToken).delete();
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, Status.OK);
	}

	private <T> Response post(final WebTarget webTarget, final T object) {
		final Callable<Response> responseCallable = () -> {
			final Entity<T> entity = Entity.entity(object, MediaType.APPLICATION_JSON);
			return webTarget.request(MediaType.APPLICATION_JSON).header(ACCESS_TOKEN_HEADER, accessToken).post(entity);
		};
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, Status.CREATED, Status.OK);
	}

	private <T> Response put(final WebTarget webTarget, final T object) {
		final Callable<Response> responseCallable = () -> {
			final Entity<T> entity = Entity.entity(object, MediaType.APPLICATION_JSON);
			return webTarget.request(MediaType.APPLICATION_JSON).header(ACCESS_TOKEN_HEADER, accessToken).put(entity);
		};
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, Status.OK);
	}

	private Response handleResponse(final Response response, final Status... expectedStatus) {
		if ((response.getHeaders() != null) && response.getHeaders().containsKey(DEPRECATED_REASON_HEADER)) {
			LOGGER.error(DEPRECATED_SHOPIFY_CALL_ERROR_MESSAGE, response.getLocation(), response.getStatus(),
					response.getStringHeaders());
		}

		final List<Integer> expectedStatusCodes = getExpectedStatusCodes(expectedStatus);
		if (expectedStatusCodes.contains(response.getStatus())) {
			return response;
		}
		throw new ShopifyErrorResponseException(response);
	}

	private List<Integer> getExpectedStatusCodes(final Status... expectedStatus) {
		return Arrays.asList(expectedStatus).stream().map(Status::getStatusCode).collect(Collectors.toList());
	}

	private Response invokeResponseCallable(final Callable<Response> responseCallable) {
		final Retryer<Response> retryer = buildResponseRetyer();
		try {
			return retryer.call(responseCallable);
		} catch (ExecutionException | RetryException e) {
			throw new ShopifyClientException(RETRY_FAILED_MESSAGE, e);
		}
	}

	private Retryer<Response> buildResponseRetyer() {

		return RetryerBuilder.<Response> newBuilder().retryIfResult(ShopifySdk::shouldRetryResponse)
				.withWaitStrategy(WaitStrategies.randomWait(2, TimeUnit.SECONDS, 30, TimeUnit.SECONDS))
				.withStopStrategy(StopStrategies.stopAfterDelay(15, TimeUnit.MINUTES))
				.withRetryListener(new ShopifySdkRetryListener()).build();
	}

	private static boolean shouldRetryResponse(final Response response) {
		return isServerError(response) || hasExceededRateLimit(response) || hasNotBeenSaved(response);
	}

	private static boolean hasExceededRateLimit(final Response response) {
		return TOO_MANY_REQUESTS_STATUS_CODE == response.getStatus();
	}

	private static boolean isServerError(final Response response) {
		return (Status.Family.SERVER_ERROR == Status.Family.familyOf(response.getStatus()))
				|| (LOCKED_STATUS_CODE == response.getStatus());
	}

	private static boolean hasNotBeenSaved(final Response response) {
		if ((UNPROCESSABLE_ENTITY_STATUS_CODE == response.getStatus()) && response.hasEntity()) {
			response.bufferEntity();
			final String shopifyErrorResponse = response.readEntity(String.class);
			return shopifyErrorResponse.contains(COULD_NOT_BE_SAVED_SHOPIFY_ERROR_MESSAGE);
		}
		return false;
	}

	private String generateToken() {
		try {

			final Entity<String> entity = Entity.entity("", MediaType.APPLICATION_JSON);
			final Response response = this.webTarget.path(OAUTH).path(ACCESS_TOKEN).queryParam(CLIENT_ID, this.clientId)
					.queryParam(CLIENT_SECRET, this.clientSecret)
					.queryParam(AUTHORIZATION_CODE, this.authorizationToken).request(MediaType.APPLICATION_JSON)
					.post(entity);

			final int status = response.getStatus();

			if (Status.OK.getStatusCode() == status) {
				final ShopifyAccessTokenRoot shopifyResponse = response.readEntity(ShopifyAccessTokenRoot.class);
				return shopifyResponse.getAccessToken();
			}
			throw new ShopifyErrorResponseException(response);
		} catch (final Exception e) {
			throw new ShopifyClientException(
					String.format(GENERAL_ACCESS_TOKEN_EXCEPTION_MESSAGE, shopSubdomain, this.authorizationToken), e);
		}
	}

	private WebTarget getWebTarget() {
		if (this.webTarget == null) {

			if (StringUtils.isNotBlank(this.shopSubdomain)) {
				this.webTarget = CLIENT.target(
						new StringBuilder().append(HTTPS).append(this.shopSubdomain).append(API_TARGET).toString());

			} else {
				this.webTarget = CLIENT.target(this.apiUrl);
			}
			if (this.accessToken == null) {
				this.accessToken = generateToken();
			}
			final Shop shop = this.getShop().getShop();
			LOGGER.info(SHOP_RETRIEVED_MESSAGE, shop.getId(), shop.getName());
		}
		return webTarget;
	}

	private static Client buildClient() {
		final ObjectMapper mapper = buildMapper();
		final JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
		provider.setMapper(mapper);
		return ClientBuilder.newClient().property(ClientProperties.CONNECT_TIMEOUT, ONE_MINUTE_IN_MILLISECONDS)
				.register(JacksonFeature.class).property(ClientProperties.READ_TIMEOUT, FIVE_MINUTES_IN_MILLISECONDS)
				.register(provider);
	}

	static ObjectMapper buildMapper() {
		final ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

		final AnnotationIntrospector pair = AnnotationIntrospector.pair(
				new JaxbAnnotationIntrospector(TypeFactory.defaultInstance()), new JacksonAnnotationIntrospector());
		mapper.setAnnotationIntrospector(pair);

		mapper.enable(MapperFeature.USE_ANNOTATIONS);
		return mapper;
	}

	private static class ShopifySdkRetryListener implements RetryListener {

		@Override
		public <V> void onRetry(final Attempt<V> attempt) {
			if (LOGGER.isWarnEnabled() && attempt.hasResult()) {
				final Response response = (Response) attempt.getResult();
				response.bufferEntity();
				if (shouldRetryResponse(response) && !hasExceededRateLimit(response)) {
					final long delaySinceFirstAttemptInMilliseconds = attempt.getDelaySinceFirstAttempt();
					final long delaySinceFirstAttemptInSeconds = TimeUnit.SECONDS
							.convert(delaySinceFirstAttemptInMilliseconds, TimeUnit.MILLISECONDS);
					LOGGER.warn(RETRY_ATTEMPT_MESSAGE, delaySinceFirstAttemptInSeconds, attempt.getAttemptNumber(),
							response.getLocation(), response.getStatus(), response.getStringHeaders(),
							response.readEntity(String.class));
				}
			}
		}
	}

}

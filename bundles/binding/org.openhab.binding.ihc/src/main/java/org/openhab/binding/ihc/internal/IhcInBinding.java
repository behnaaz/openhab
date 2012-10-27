/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.ihc.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.openhab.binding.ihc.IhcBindingProvider;
import org.openhab.binding.ihc.utcs.IhcClient;
import org.openhab.binding.ihc.utcs.WSBooleanValue;
import org.openhab.binding.ihc.utcs.WSControllerState;
import org.openhab.binding.ihc.utcs.WSDateValue;
import org.openhab.binding.ihc.utcs.WSEnumValue;
import org.openhab.binding.ihc.utcs.WSFloatingPointValue;
import org.openhab.binding.ihc.utcs.WSIntegerValue;
import org.openhab.binding.ihc.utcs.WSResourceValue;
import org.openhab.binding.ihc.utcs.WSTimeValue;
import org.openhab.binding.ihc.utcs.WSTimerValue;
import org.openhab.binding.ihc.utcs.WSWeekdayValue;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingChangeListener;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DateTimeItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IhcInBinding order runtime value notifications from IHC / ELKO LS controller
 * and post values to the openHAB event bus when notification is received.
 * 
 * Binding also polls resources from controller where interval is configured.
 * 
 * @author Pauli Anttila
 * @since 1.1.0
 */
public class IhcInBinding extends AbstractActiveBinding<IhcBindingProvider>
		implements ManagedService, BindingChangeListener {

	private static final Logger logger = LoggerFactory
			.getLogger(IhcInBinding.class);

	private boolean isProperlyConfigured = false;
	private ItemRegistry itemRegistry;
	private long refreshInterval = 1000;

	/** Thread to handle resource value notifications from the controller */
	private IhcResourceValueNotificationListener resourceValueNotificationListener = null;

	/** Thread to handle controller's state change notifications */
	private IhcControllerStateListener controllerStateListener = null;

	/** Holds time in seconds when configuration is changed */
	private long LastConfigurationChangeTime = 0;

	/** Holds time stamps in seconds when binding items states are refreshed */
	private Map<String, Long> lastUpdateMap = new HashMap<String, Long>();

	@Override
	protected String getName() {
		return "IHC / ELKO LS refresh and notification listener service";
	}

	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	public void setItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = itemRegistry;
	}

	public void unsetItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = null;
	}

	public void activate(ComponentContext componentContext) {
		logger.debug("Activate");

		resourceValueNotificationListener = new IhcResourceValueNotificationListener();
		resourceValueNotificationListener.start();
		controllerStateListener = new IhcControllerStateListener();
		controllerStateListener.start();

	}

	public void deactivate(ComponentContext componentContext) {
		logger.debug("Deactivate");
		for (IhcBindingProvider provider : providers) {
			provider.removeBindingChangeListener(this);
		}
		providers.clear();
		resourceValueNotificationListener.setInterrupted(true);
		controllerStateListener.setInterrupted(true);
	}

	public synchronized void touchLastConfigurationChangeTime() {
		LastConfigurationChangeTime = System.currentTimeMillis();
	}

	public synchronized long getLastConfigurationChangeTime() {
		return LastConfigurationChangeTime;
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	public boolean isProperlyConfigured() {
		return isProperlyConfigured;
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	public void execute() {

		IhcClient ihc = IhcConnection.getCommunicator();

		if (ihc != null) {
			for (IhcBindingProvider provider : providers) {
				for (String itemName : provider.getItemNames()) {

					int resourceId = provider.getResourceId(itemName);
					int itemRefreshInterval = provider
							.getRefreshInterval(itemName) * 1000;

					if (itemRefreshInterval > 0) {

						Long lastUpdateTimeStamp = lastUpdateMap.get(itemName);
						if (lastUpdateTimeStamp == null) {
							lastUpdateTimeStamp = 0L;
						}

						long age = System.currentTimeMillis()
								- lastUpdateTimeStamp;
						boolean needsUpdate = age >= itemRefreshInterval;

						if (needsUpdate) {

							logger.debug(
									"Item '{}' is about to be refreshed now",
									itemName);

							try {
								WSResourceValue resourceValue = null;

								try {
									resourceValue = ihc
											.resourceQuery(resourceId);
								} catch (IOException e1) {
									logger.warn("Value could not be read from controller - retrying one time.");

									try {
										IhcConnection.reconnect();
										resourceValue = ihc
												.resourceQuery(resourceId);
									} catch (Exception e2) {
										logger.error("Communication error", e2);
									}

								}

								if (resourceValue != null) {
									Item item = getItemFromItemName(itemName);
									State value = convertResourceValueToState(
											item, resourceValue);
									eventPublisher.postUpdate(itemName, value);
								}

							} catch (Exception e) {
								logger.error("Exception", e);
							}

							lastUpdateMap.put(itemName,
									System.currentTimeMillis());
						}
					}
				}
			}
		} else {
			logger.warn("Controller is null => refresh cycle aborted!");
		}

	}

	/**
	 * Convert IHC data type to openHAB data type.
	 * 
	 * @param year
	 * 
	 * @param type
	 *            IHC data type
	 * 
	 * @return openHAB data type
	 */
	private State convertResourceValueToState(Item item, WSResourceValue value)
			throws NumberFormatException {

		org.openhab.core.types.State state = UnDefType.UNDEF;

		if (item instanceof NumberItem) {

			if (value.getClass() == WSFloatingPointValue.class) {
				// state = new
				// DecimalType(((WSFloatingPointValue)value).getFloatingPointValue());

				// Controller might send floating point value with >10 decimals
				// (22.299999237060546875), so round value to have max 2
				// decimals
				double d = ((WSFloatingPointValue) value)
						.getFloatingPointValue();
				BigDecimal bd = new BigDecimal(d).setScale(2,
						RoundingMode.HALF_EVEN);
				state = new DecimalType(bd);
			}

			else if (value.getClass() == WSBooleanValue.class)
				state = new DecimalType(((WSBooleanValue) value).isValue() ? 1
						: 0);

			else if (value.getClass() == WSIntegerValue.class)
				state = new DecimalType(((WSIntegerValue) value).getInteger());

			else if (value.getClass() == WSTimerValue.class)
				state = new DecimalType(
						((WSTimerValue) value).getMilliseconds());

			else if (value.getClass() == WSWeekdayValue.class)
				state = new DecimalType(
						((WSWeekdayValue) value).getWeekdayNumber());

			else
				throw new NumberFormatException("Can't convert "
						+ value.getClass().toString() + " to NumberItem");

		} else if (item instanceof SwitchItem) {

			if (value.getClass() == WSBooleanValue.class) {
				if (((WSBooleanValue) value).isValue())
					state = OnOffType.ON;
				else
					state = OnOffType.OFF;
			} else {
				throw new NumberFormatException("Can't convert "
						+ value.getClass().toString() + " to SwitchItem");
			}

		} else if (item instanceof ContactItem) {

			if (value.getClass() == WSBooleanValue.class) {
				if (((WSBooleanValue) value).isValue())
					state = OpenClosedType.OPEN;
				else
					state = OpenClosedType.CLOSED;
			} else {
				throw new NumberFormatException("Can't convert "
						+ value.getClass().toString() + " to ContactItem");
			}

		} else if (item instanceof DateTimeItem) {

			if (value.getClass() == WSDateValue.class) {

				Calendar cal = WSDateTimeToCalendar((WSDateValue) value, null);
				state = new DateTimeType(cal);

			} else if (value.getClass() == WSTimeValue.class) {

				Calendar cal = WSDateTimeToCalendar(null, (WSTimeValue) value);
				state = new DateTimeType(cal);

			} else {

				throw new NumberFormatException("Can't convert "
						+ value.getClass().toString() + " to DateTimeItem");
			}

		} else if (item instanceof StringItem) {

			if (value.getClass() == WSEnumValue.class) {

				state = new StringType(((WSEnumValue) value).getEnumName());

			} else {

				throw new NumberFormatException("Can't convert "
						+ value.getClass().toString() + " to StringItem");
			}
		}

		return state;
	}

	private Calendar WSDateTimeToCalendar(WSDateValue date, WSTimeValue time) {

		Calendar cal = new GregorianCalendar(1900, 01, 01);

		if (date != null) {
			short year = date.getYear();
			short month = date.getMonth();
			short day = date.getDay();

			cal.set(year, month, day, 0, 0, 0);
		}

		if (time != null) {
			int hour = time.getHours();
			int minute = time.getMinutes();
			int second = time.getSeconds();

			cal.set(1900, 1, 1, hour, minute, second);
		}

		return cal;
	}

	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		touchLastConfigurationChangeTime();
		super.bindingChanged(provider, itemName);
	}

	@SuppressWarnings("rawtypes")
	public void updated(Dictionary config) throws ConfigurationException {
		logger.debug("Configuration updated, config {}", config != null ? true
				: false);

		touchLastConfigurationChangeTime();

		if (config != null) {

		}

		isProperlyConfigured = true;
	}

	/**
	 * Returns the {@link Item} for the given <code>itemName</code> or
	 * <code>null</code> if there is no or to many corresponding Items
	 * 
	 * @param itemName
	 * 
	 * @return the {@link Item} for the given <code>itemName</code> or
	 *         <code>null</code> if there is no or to many corresponding Items
	 */
	private Item getItemFromItemName(String itemName) {
		try {
			return itemRegistry.getItem(itemName);
		} catch (ItemNotFoundException e) {
			logger.error("Couldn't find item for itemName '" + itemName + "'");
		}

		return null;
	}

	/**
	 * The IhcReader runs as a separate thread.
	 * 
	 * Thread listen resource value notifications from IHC / ELKO LS controller
	 * and post updates to openHAB bus when notifications are received.
	 * 
	 */
	private class IhcResourceValueNotificationListener extends Thread {

		private boolean interrupted = false;
		private long lastNotificationOrderTime = 0;

		IhcResourceValueNotificationListener() {
		}

		public void setInterrupted(boolean interrupted) {
			this.interrupted = interrupted;
		}

		private void enableResourceValueNotification()
				throws UnsupportedEncodingException, XPathExpressionException,
				IOException {
			logger.debug("Order resource runtime value notifications from controller");

			List<Integer> resourceIdList = new ArrayList<Integer>();

			IhcClient ihc = IhcConnection.getCommunicator();

			if (ihc != null) {
				for (IhcBindingProvider provider : providers) {
					for (String itemName : provider.getItemNames()) {
						resourceIdList.add(provider.getResourceId(itemName));
					}
				}
			}

			if (resourceIdList.size() > 0) {
				logger.debug("Enable runtime notfications for {} resources",
						resourceIdList.size());
				ihc.enableRuntimeValueNotifications(resourceIdList);
				lastNotificationOrderTime = System.currentTimeMillis();
			}
		}

		@Override
		public void run() {

			logger.debug("IHC Listener started");

			boolean ready = false;

			// as long as no interrupt is requested, continue running
			while (!interrupted) {

				boolean orderResourceValueNotifications = false;

				final long lastConfigChangeTime = getLastConfigurationChangeTime();

				if (lastConfigChangeTime > lastNotificationOrderTime) {

					logger.debug("Configuration change detected");

					ready = false;

					if ((lastConfigChangeTime + 1000) < System
							.currentTimeMillis()) {

						ready = true;
						orderResourceValueNotifications = true;

					} else {
						logger.debug("Waiting 1 seconds before reorder runtime value notifications");
					}
				}

				if (IhcConnection.getLastOpenTime() > lastNotificationOrderTime) {
					logger.debug("Controller connection reopen detected");
					orderResourceValueNotifications = true;
				}

				if (ready)
					waitResourceNotifications(orderResourceValueNotifications);
				else
					mysleep(1000L);
			}

			logger.debug("IHC Listener stopped");

		}

		private void waitResourceNotifications(
				boolean orderResourceValueNotifications) {

			IhcClient ihc = IhcConnection.getCommunicator();

			if (ihc != null) {

				try {

					if (orderResourceValueNotifications)
						enableResourceValueNotification();

					logger.debug("Wait new notifications from controller");

					List<? extends WSResourceValue> resourceValueList = ihc
							.waitResourceValueNotifications(10);

					logger.debug(
							"{} new notifications received from controller",
							resourceValueList.size());

					for (WSResourceValue val : resourceValueList) {
						for (IhcBindingProvider provider : providers) {
							for (String itemName : provider.getItemNames()) {

								int resourceId = provider
										.getResourceId(itemName);

								if (val.getResourceID() == resourceId) {

									if (provider.isOutBindingOnly(itemName)) {

										logger.debug(
												"{} is out binding only...skip update to OpenHAB bus",
												itemName);

									} else {

										Item item = getItemFromItemName(itemName);
										org.openhab.core.types.State value = convertResourceValueToState(
												item, val);
										eventPublisher.postUpdate(itemName,
												value);

									}
								}

							}
						}
					}

				} catch (SocketTimeoutException e2) {
					logger.debug("Notifications timeout - no new notifications");

				} catch (IOException e) {
					logger.error(
							"New notifications wait failed...reinitialize connection",
							e);

					try {
						ihc.openConnection();
						enableResourceValueNotification();

					} catch (Exception e2) {
						logger.error("Communication error", e2);

						// fatal error occurred, be sure that notifications is
						// reordered
						lastNotificationOrderTime = 0;

						// sleep a while, before retry
						mysleep(1000L);
					}
				} catch (Exception e) {
					logger.error("Exception", e);

					// sleep a while, before retry
					mysleep(5000L);
				}

			} else {
				logger.warn("Controller is null => resource value notfications waiting aborted!");
				mysleep(5000L);
			}

		}

		private void mysleep(long milli) {
			try {
				sleep(5000L);
			} catch (InterruptedException e3) {
				interrupted = true;
			}
		}
	}

	/**
	 * The IhcReader runs as a separate thread.
	 * 
	 * Thread listen controller state change notifications from IHC / ELKO LS
	 * controller and .
	 * 
	 */
	private class IhcControllerStateListener extends Thread {

		private boolean interrupted = false;

		IhcControllerStateListener() {
		}

		public void setInterrupted(boolean interrupted) {
			this.interrupted = interrupted;
		}

		@Override
		public void run() {

			logger.debug("IHC Listener started");

			WSControllerState oldState = null;

			// as long as no interrupt is requested, continue running
			while (!interrupted) {

				IhcClient ihc = IhcConnection.getCommunicator();

				if (ihc != null) {

					try {

						if (oldState == null) {

							oldState = ihc.queryControllerState();
							logger.debug("Controller initial state {}",
									oldState.getState());
						}

						logger.debug("Wait new state change notification from controller");

						WSControllerState currentState = ihc
								.waitStateChangeNotifications(oldState, 10);
						logger.debug("Controller state {}",
								currentState.getState());

						if (oldState.getState().equals(currentState.getState()) == false) {
							logger.info(
									"Controller state change detected ({} -> {})",
									oldState.getState(),
									currentState.getState());

							if (oldState.getState().equals(
									IhcClient.CONTROLLER_STATE_INITIALIZE)
									|| currentState.getState().equals(
											IhcClient.CONTROLLER_STATE_READY)) {

								logger.debug("Reopen connection...");
								IhcConnection.connect();
							}

							oldState.setState(currentState.getState());
						}

					} catch (IOException e) {
						logger.error(
								"New controller state change notification wait failed...reinitialize connection",
								e);

						try {
							IhcConnection.reconnect();

						} catch (Exception e2) {
							logger.error("Communication error", e2);

							// sleep a while, before retry
							mysleep(1000L);
						}
					} catch (Exception e) {
						logger.error("Exception", e);

						// sleep a while, before retry
						mysleep(5000L);
					}

				} else {
					logger.warn("Controller is null => resource value notfications waiting aborted!");
					mysleep(5000L);
				}
			}

		}

		private void mysleep(long milli) {
			try {
				sleep(5000L);
			} catch (InterruptedException e3) {
				interrupted = true;
			}
		}
	}

}

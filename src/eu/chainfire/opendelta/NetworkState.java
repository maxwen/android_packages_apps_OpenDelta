/* 
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
 * Copyright (C) 2013 The OmniROM Project
 */
/* 
 * This file is part of OpenDelta.
 * 
 * OpenDelta is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * OpenDelta is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with OpenDelta. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.chainfire.opendelta;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkState {
	public interface OnNetworkStateListener {
		public void onNetworkState(boolean state);
	}
	
	private Context context = null;
	private OnNetworkStateListener onNetworkStateListener = null; 
	private ConnectivityManager connectivityManager = null;
	private volatile Boolean stateLast = null;

	private boolean wifiOnly = false;
	
	private IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
	private BroadcastReceiver receiver = new BroadcastReceiver() {		
		@Override
		public void onReceive(Context context, Intent intent) {		
			updateState();
		}
	};

	private void updateState() {
		if (onNetworkStateListener != null) {
			NetworkInfo info = connectivityManager.getActiveNetworkInfo();
		
			boolean state = (
				(info != null) && 
				info.isConnected() && 
				(
					!wifiOnly || 
					(info.getType() == ConnectivityManager.TYPE_WIFI) ||
					(info.getType() == ConnectivityManager.TYPE_ETHERNET)
				)
			);
			
			if ((stateLast == null) || (stateLast != state)) {
				stateLast = state;
				onNetworkStateListener.onNetworkState(state);
			}
		}		
	}		
		
	public boolean start(Context context, OnNetworkStateListener onNetworkStateListener, boolean wifiOnly) {
		if (this.context == null) {
			this.context = context;
			this.onNetworkStateListener = onNetworkStateListener;
			this.wifiOnly = wifiOnly;
			connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
			context.registerReceiver(receiver, filter);
			updateState();
			return true;
		}
		return false;
	}
	
	public boolean stop() {
		if (context != null) {
			context.unregisterReceiver(receiver);
			onNetworkStateListener = null;
			connectivityManager = null;
			context = null;
			return true;
		}		
		return false;
	}
	
	public Boolean getState() {
		if (stateLast == null) return false;
		return stateLast.booleanValue();
	}
}

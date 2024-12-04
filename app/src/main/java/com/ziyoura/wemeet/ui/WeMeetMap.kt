package com.ziyoura.wemeet.ui

import android.location.Location
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ziyoura.wemeet.R
import com.ziyoura.wemeet.presentation.WeMeetViewModel

private const val TAG = "WeMeetMap"
private const val zoom = 8f

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun WeMeetMap(viewModel: WeMeetViewModel) {
    var isMapLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapId = stringResource(id = R.string.map_id)

    val cameraPositionState = rememberCameraPositionState {
        CameraPosition.Builder()
            .target(LatLng(39.4, 115.7))
            .zoom(15f)
            .build()
    }

    val mapProperties by remember {
        mutableStateOf(
            MapProperties(
                mapType = MapType.NORMAL,
                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.style_json),
                isMyLocationEnabled = true
            )
        )
    }

    val locationState by viewModel.locationState.collectAsState()
    val locationSource = remember { MyLocationSource() }
    var hasInitialPosition by remember { mutableStateOf(false) }

    // 添加其他用户位置状态
    val otherUsersLocations by viewModel.otherUsersLocations.collectAsState()

    // Update the camera position when the location changes
    LaunchedEffect(locationState) {
        Log.d("xqc", "Location state changed: $locationState")
        when (val state = locationState) {
            is WeMeetViewModel.LocationState.Success -> {
                val currentLatLng = LatLng(state.location.latitude, state.location.longitude)
                locationSource.onLocationChanged(state.location)
                Log.d("xqc", "Location updated: $currentLatLng")
                // 只在首次定位时移动摄像机
                if (!hasInitialPosition) {
                    Log.d("xqc", "Move camera to $currentLatLng")
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, zoom)
                    hasInitialPosition = true
                }
            }
            is WeMeetViewModel.LocationState.Error -> {
                Log.d("xqc", "Location error: ${state.message}")

            }
            is WeMeetViewModel.LocationState.Loading -> {
                Log.d("xqc", "Loading location")
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            locationSource = locationSource,
            onMapLoaded = { isMapLoaded = true },
            onMyLocationClick = {
                Log.d(TAG, "My location clicked")
                viewModel.getCurrentLocation()
            },
            googleMapOptionsFactory = {
                GoogleMapOptions().mapId(mapId)
            }
        ) {
            // 在地图上显示其他用户的位置
            otherUsersLocations.forEach { (_, userLocationInfo) ->
                Marker(
                    state = MarkerState(position = userLocationInfo.location),
                    title = "用户 ${userLocationInfo.username}", // 显示用户ID的第一个字符
                    snippet = "其他用户",
                    icon = BitmapDescriptorFactory.defaultMarker(
                        // 根据用户ID生成不同的颜色
                        (userLocationInfo.userId.hashCode() * 30f) % 360f
                    )
                )
            }
        }



        if (!isMapLoaded) {
            AnimatedVisibility(
                modifier = Modifier.matchParentSize(),
                visible = !isMapLoaded,
                enter = EnterTransition.None,
                exit = fadeOut()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .wrapContentSize()
                )
            }
        }
    }
}

private class MyLocationSource : LocationSource {

    private var listener: OnLocationChangedListener? = null

    override fun activate(listener: OnLocationChangedListener) {
        this.listener = listener
    }

    override fun deactivate() {
        listener = null
    }

    fun onLocationChanged(location: Location) {
        listener?.onLocationChanged(location)
    }
}

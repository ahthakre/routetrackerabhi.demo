package com.abhishek.routetrackerabhi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.FirebaseDatabase;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    // Map a marker id to its corresponding list (represented by the root marker id)
    HashMap<String, String> markerToList = new HashMap<>();

    // A list of markers for each polygon (designated by the marker root).
    HashMap<String, List<Marker>> polylineMarkers = new HashMap<>();

    // A list of polygon points for each polygon (designed by the marker root).
    HashMap<String, List<LatLng>> polylinePoints = new HashMap<>();

    // List of polygons (designated by marker root).
    HashMap<String, Polyline> polylines = new HashMap<>();

    // The active polygon (designated by marker root) - polygon added to.
    String markerListKey;

    // Flag used to record when the 'New Polygon' button is pressed.  Next map
// click starts a new polygon.
    boolean newPolyline = false;

    private GoogleMap mMap;
    private List<LatLng> points = new ArrayList<>();
    private List<Marker> markerList = new ArrayList<>();

    PolylineOptions polylineOptions;
    Button buttonDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_maps);

        buttonDatabase = (Button)findViewById(R.id.btn_database);
        buttonDatabase.setOnClickListener( this );


            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);

            if (savedInstanceState == null) {
                mapFragment.getMapAsync(this);
            }

            mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType( GoogleMap.MAP_TYPE_HYBRID );
        CameraUpdate center =
                CameraUpdateFactory.newLatLng(new LatLng(19.076090, 72.877426));
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(11);
        mMap.moveCamera(center);
        mMap.animateCamera(zoom);
        mMap.setIndoorEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {

                Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
                marker.setTag(latLng);
                marker.setTitle( latLng.latitude + " : " + latLng.longitude );

                // Special case for very first marker.
                if (polylineMarkers.size() == 0) {
                    polylineMarkers.put(marker.getId(), new ArrayList<Marker>());
                    // only 0 or 1 polygons so just add it to new one or existing one.
                    markerList = new ArrayList<>();
                    points = new ArrayList<>();
                    polylineMarkers.put(marker.getId(), markerList);
                    polylinePoints.put(marker.getId(), points);
                    markerListKey = marker.getId();
                }

                if (newPolyline) {
                    newPolyline = false;
                    markerList = new ArrayList<>();
                    points = new ArrayList<>();
                    polylineMarkers.put(marker.getId(),markerList);
                    polylinePoints.put(marker.getId(),points);
                    markerListKey = marker.getId();
                }

                markerList.add(marker);
                points.add(latLng);
                markerToList.put(marker.getId(), markerListKey);

                drawPolyline(markerListKey, points);


            }
        });



        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {

            }

            @Override
            public void onMarkerDrag(Marker marker) {
                updateMarkerLocation(marker, false);
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updateMarkerLocation(marker, true);

            }
        });
        mMap.setOnMapLongClickListener( new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull LatLng latLng) {

                mMap.clear();

                polylineOptions.color( Color.BLACK);
                polylineOptions.width(15);

                googleMap.addPolyline(polylineOptions);

            }
        } );




        enableMyLocation();

    }


    public void newPolyline(View view) {
        newPolyline = true;
    }

    private void updateMarkerLocation(Marker marker, boolean calculate) {

        // Use the marker to figure out which polygon list to use...
        List<LatLng> pts = polylinePoints.get(markerToList.get(marker.getId()));

        // This is much the same except use the retrieved point list.
        LatLng latLng = (LatLng) marker.getTag();
        int position = pts.indexOf(latLng);
        pts.set(position, marker.getPosition());
        marker.setTag(marker.getPosition());
        drawPolyline(markerToList.get(marker.getId()), pts);

    }


    private void drawPolyline(String mKey, List<LatLng> latLngList) {

        // Use the existing polygon (if any) for the root marker.
        Polyline polyline = polylines.get(mKey);
        if (polyline != null) {
            polyline.remove();
        }
        polylineOptions = new PolylineOptions();
        polylineOptions.addAll(latLngList);
        polylineOptions.color( Color.RED);
        polyline = mMap.addPolyline(polylineOptions);

        // And update the list for the root marker.
        polylines.put(mKey, polyline);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Change the map type based on the user's selection.
        switch (item.getItemId()) {
            case R.id.normal_map:
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                return true;
            case R.id.hybrid_map:
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                return true;
            case R.id.satellite_map:
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                return true;
            case R.id.terrain_map:
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this, new String[]
                            {Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Check if location permissions are granted and if so enable the
        // location data layer.
        super.onRequestPermissionsResult( requestCode, permissions, grantResults );
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation();
                    break;
                }
        }
    }
    public void searchLocation(View view) {
        EditText locationSearch = (EditText) findViewById(R.id.editText);
        String location = locationSearch.getText().toString();
        List<Address> addressList = null;

        if (location != null || !location.equals("")) {
            Geocoder geocoder = new Geocoder(this);
            try {
                addressList = geocoder.getFromLocationName(location, 1);

            } catch (IOException e) {
                e.printStackTrace();
            }
            Address address = addressList.get(0);
            LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
            mMap.addMarker(new MarkerOptions().position(latLng).title(location));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    latLng, 20
            ));
            Toast.makeText(getApplicationContext(),address.getLatitude()+" "+address.getLongitude(),Toast.LENGTH_LONG).show();
        }
    }

    public void onClick(View v) {

        if(v==buttonDatabase){

            FirebaseDatabase.getInstance().getReference("current location").setValue( points)
                    .addOnCompleteListener( new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()){
                                Toast.makeText( MapsActivity.this, "LOCATION SAVED", Toast.LENGTH_SHORT ).show();
                            }
                            else{
                                Toast.makeText( MapsActivity.this, "LOCATION NOT SAVED", Toast.LENGTH_SHORT ).show();
                            }
                        }
                    } );
        }

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged( hasCapture );
    }



    public void btnexcel(View view) {
        Workbook wb=new HSSFWorkbook();
        Cell cell=null;
        CellStyle cellStyle=wb.createCellStyle();
        cellStyle.setFillForegroundColor( HSSFColor.LIGHT_BLUE.index);
        cellStyle.setFillPattern( HSSFCellStyle.SOLID_FOREGROUND);

        //Now we are creating sheet
        Sheet sheet=null;
        sheet = wb.createSheet("coordinates");
        //Now column and row
        Row row =sheet.createRow(0);

        cell=row.createCell(0);
        cell.setCellValue("LATITUDE");
        cell.setCellStyle(cellStyle);

        cell=row.createCell(1);
        cell.setCellValue("LONGITUDE");
        cell.setCellStyle(cellStyle);

        HSSFRow ROW = (HSSFRow) sheet.createRow( (short) 1);
        ROW.createCell( 0 ).setCellValue( 19.23535107 );
        ROW.createCell( 1).setCellValue( 72.94828936 );

        HSSFRow ROW1 = (HSSFRow) sheet.createRow( (short) 2);
        ROW1.createCell( 0 ).setCellValue(  19.2650628);
        ROW1.createCell( 1).setCellValue( 73.01060278 );

        HSSFRow ROW2 = (HSSFRow) sheet.createRow( (short) 3);
        ROW2.createCell( 0 ).setCellValue( 19.33893813 );
        ROW2.createCell( 1).setCellValue( 73.00316568 );

        HSSFRow ROW3 = (HSSFRow) sheet.createRow( (short) 4);
        ROW3.createCell( 0 ).setCellValue( 19.41078536);
        ROW3.createCell( 1).setCellValue( 73.01913019 );

        HSSFRow ROW4 = (HSSFRow) sheet.createRow( (short) 5);
        ROW4.createCell( 0 ).setCellValue( 19.48320235	);
        ROW4.createCell( 1).setCellValue( 72.98175193 );

        HSSFRow ROW5 = (HSSFRow) sheet.createRow( (short) 6);
        ROW5.createCell( 0 ).setCellValue(  19.50774489	);
        ROW5.createCell( 1).setCellValue( 73.04297805 );

        HSSFRow ROW6 = (HSSFRow) sheet.createRow( (short) 7);
        ROW6.createCell( 0 ).setCellValue(  19.50774489	 );
        ROW6.createCell( 1).setCellValue( 73.04297805 );

        HSSFRow ROW7 = (HSSFRow) sheet.createRow( (short) 8);
        ROW7.createCell( 0 ).setCellValue( 19.6039818	);
        ROW7.createCell( 1).setCellValue( 73.05550598 );

        HSSFRow ROW8 = (HSSFRow) sheet.createRow( (short) 9);
        ROW8.createCell( 0 ).setCellValue( 19.70502088 );
        ROW8.createCell( 1).setCellValue( 73.05471539 );

        HSSFRow ROW9 = (HSSFRow) sheet.createRow( (short) 10);
        ROW9.createCell( 0 ).setCellValue( 19.79183722	);
        ROW9.createCell( 1).setCellValue( 73.06969922 );

        HSSFRow ROW10 = (HSSFRow) sheet.createRow( (short) 11);
        ROW10.createCell( 0 ).setCellValue( 19.87843598	);
        ROW10.createCell( 1).setCellValue( 73.08993079 );

        HSSFRow ROW11 = (HSSFRow) sheet.createRow( (short) 12);
        ROW11.createCell( 0 ).setCellValue( 19.87843598	 );
        ROW11.createCell( 1).setCellValue( 73.08993079 );

        HSSFRow ROW12 = (HSSFRow) sheet.createRow( (short) 13);
        ROW12.createCell( 0 ).setCellValue(  19.9664972	 );
        ROW12.createCell( 1).setCellValue( 73.104176);

        HSSFRow ROW13 = (HSSFRow) sheet.createRow( (short) 14);
        ROW13.createCell( 0 ).setCellValue(  20.09009271	 );
        ROW13.createCell( 1).setCellValue( 73.11988235 );

        HSSFRow ROW14 = (HSSFRow) sheet.createRow( (short) 15);
        ROW14.createCell( 0 ).setCellValue(  20.20838488	 );
        ROW14.createCell( 1).setCellValue( 73.13440382 );

        HSSFRow ROW15 = (HSSFRow) sheet.createRow( (short) 16);
        ROW15.createCell( 0 ).setCellValue( 20.20838488	);
        ROW15.createCell( 1).setCellValue( 73.13440382 );

        HSSFRow ROW16 = (HSSFRow) sheet.createRow( (short) 17);
        ROW16.createCell( 0 ).setCellValue(  20.3097327	 );
        ROW16.createCell( 1).setCellValue( 73.12777944 );

        HSSFRow ROW17 = (HSSFRow) sheet.createRow( (short) 18);
        ROW17.createCell( 0 ).setCellValue( 20.40473962	 );
        ROW17.createCell( 1).setCellValue( 73.11928455);

        HSSFRow ROW18 = (HSSFRow) sheet.createRow( (short) 19);
        ROW18.createCell( 0 ).setCellValue( 20.49920466	 );
        ROW18.createCell( 1).setCellValue( 73.11916988 );

        HSSFRow ROW19 = (HSSFRow) sheet.createRow( (short) 20);
        ROW19.createCell( 0 ).setCellValue( 20.49920466	 );
        ROW19.createCell( 1).setCellValue( 73.11916988 );

        HSSFRow ROW20 = (HSSFRow) sheet.createRow( (short) 21);
        ROW20.createCell( 0 ).setCellValue( 20.49920466	);
        ROW20.createCell( 1).setCellValue( 73.11916988 );

        HSSFRow ROW21 = (HSSFRow) sheet.createRow( (short) 22);
        ROW21.createCell( 0 ).setCellValue(  20.59460265	 );
        ROW21.createCell( 1).setCellValue( 73.12258333 );

        HSSFRow ROW22 = (HSSFRow) sheet.createRow( (short) 23);
        ROW22.createCell( 0 ).setCellValue( 20.69033337	 );
        ROW22.createCell( 1).setCellValue( 73.13409939 );

        HSSFRow ROW23 = (HSSFRow) sheet.createRow( (short) 24);
        ROW23.createCell( 0 ).setCellValue( 20.7869751	);
        ROW23.createCell( 1).setCellValue( 	73.13869737 );

        HSSFRow ROW24 = (HSSFRow) sheet.createRow( (short) 25);
        ROW24.createCell( 0 ).setCellValue( 20.86754359	 );
        ROW24.createCell( 1).setCellValue( 73.14199146 );

        HSSFRow ROW25 = (HSSFRow) sheet.createRow( (short) 26);
        ROW25.createCell( 0 ).setCellValue( 20.9532249	);
        ROW25.createCell( 1).setCellValue( 73.14565267  );

        HSSFRow ROW26 = (HSSFRow) sheet.createRow( (short) 27);
        ROW26.createCell( 0 ).setCellValue( 20.9532249	 );
        ROW26.createCell( 1).setCellValue( 73.14565267 );

         HSSFRow ROW27 = (HSSFRow) sheet.createRow( (short) 28);
        ROW27.createCell( 0 ).setCellValue( 20.9532249	 );
        ROW27.createCell( 1).setCellValue( 73.14565267 );


        sheet.setColumnWidth(0,(10*200));
        sheet.setColumnWidth(1,(10*200));

        File file = new File(getExternalFilesDir(null),"LATLNG.xls");
        FileOutputStream outputStream =null;

        try {
            outputStream=new FileOutputStream(file);
            wb.write(outputStream);
            Toast.makeText(getApplicationContext(),"OK",Toast.LENGTH_LONG).show();
        } catch (java.io.IOException e) {
            e.printStackTrace();

            Toast.makeText(getApplicationContext(),"NO OK",Toast.LENGTH_LONG).show();
            try {
                outputStream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}

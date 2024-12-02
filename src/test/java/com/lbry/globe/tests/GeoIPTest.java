package com.lbry.globe.tests;

import com.lbry.globe.util.GeoIP;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class GeoIPTest{

    @Test
    public void testGetCoordinateFromLocation(){
        assertArrayEquals(new Double[]{123.0,456.0},GeoIP.getCoordinateFromLocation("123,456"));
        assertArrayEquals(new Double[]{123.125,456.125},GeoIP.getCoordinateFromLocation("123.125,456.125"));
        assertArrayEquals(new Double[]{123.25,456.25},GeoIP.getCoordinateFromLocation("123.25,456.25"));
        assertArrayEquals(new Double[]{123.5,456.5},GeoIP.getCoordinateFromLocation("123.5,456.5"));
    }

}
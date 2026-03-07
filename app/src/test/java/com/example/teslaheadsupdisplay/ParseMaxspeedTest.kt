package com.chaitalkstech.teslaheadsupdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseMaxspeedTest {

    @Test fun mph_value_returned_as_is() =
        assertEquals("45", parseMaxspeed("45 mph"))

    @Test fun kmh_converted_to_mph() =
        assertEquals("62", parseMaxspeed("100 km/h"))

    @Test fun kph_converted_to_mph() =
        assertEquals("37", parseMaxspeed("60 kph"))

    @Test fun bare_integer_treated_as_kmh() =
        assertEquals("37", parseMaxspeed("60"))

    @Test fun unknown_value_returns_null() =
        assertNull(parseMaxspeed("none"))

    @Test fun walk_returns_null() =
        assertNull(parseMaxspeed("walk"))

    @Test fun variable_returns_null() =
        assertNull(parseMaxspeed("variable"))

    @Test fun mph_case_insensitive() =
        assertEquals("35", parseMaxspeed("35 MPH"))

    @Test fun blank_string_returns_null() =
        assertNull(parseMaxspeed("   "))
}

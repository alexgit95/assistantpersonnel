package com.assistant.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

class MobileControllerTest {

    View noRendering = (model, request, response) -> { };
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new MobileController()).setSingleView(noRendering).build();

    @Test
    void mobilePagesAreServedWithoutCacheNorReferrer() throws Exception {
        for (String path : new String[] {"/mobile", "/mobile/enroll", "/mobile/weight"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }
    }

    @Test
    void eachPageUsesItsOwnTemplate() throws Exception {
        mvc.perform(get("/mobile")).andExpect(view().name("mobile"));
        mvc.perform(get("/mobile/enroll")).andExpect(view().name("mobile-enroll"));
        mvc.perform(get("/mobile/weight")).andExpect(view().name("mobile-weight"));
    }
}

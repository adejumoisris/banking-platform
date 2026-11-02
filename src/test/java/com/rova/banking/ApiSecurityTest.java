package com.rova.banking;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles({"local","test"})
class ApiSecurityTest {
 @Autowired MockMvc mvc;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Test void anonymousCannotReadAccounts() throws Exception { mvc.perform(get("/api/v1/accounts/00000000-0000-0000-0000-000000000001")).andExpect(status().isUnauthorized()); }
 @Test @WithMockUser(roles="AUDITOR") void auditorCannotCreateCustomer() throws Exception {
  mvc.perform(post("/api/v1/customers").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fullName","Ada","email","ada@example.com")))).andExpect(status().isForbidden());
 }
 @Test @WithMockUser(roles="OPERATIONS") void rejectsInvalidCustomer() throws Exception {
  mvc.perform(post("/api/v1/customers").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fullName","","email","invalid")))).andExpect(status().isBadRequest());
 }
}

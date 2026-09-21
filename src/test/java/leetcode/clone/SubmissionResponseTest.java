package leetcode.clone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionResponseTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void post_returnsIdAndSubmitStatus() throws Exception {
        mockMvc.perform(post("/api/v1/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"problemId":1,"language":"PYTHON","codeSubmit":"print(1)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.submitStatus").value("PENDING"))
                .andExpect(jsonPath("$.problem").doesNotExist());
    }

    @Test
    void get_returnsSubmitStatusWithoutProblem() throws Exception {
        mockMvc.perform(get("/api/v1/submission/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitStatus").exists())
                .andExpect(jsonPath("$.problem").doesNotExist());
    }
}

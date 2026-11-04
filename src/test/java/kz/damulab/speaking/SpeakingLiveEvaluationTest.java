package kz.damulab.speaking;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import kz.damulab.ai.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;

/** Только явный платный QA: синтетический корпус, собственная H2, ключ из process env, результат вне Git. */
@Tag("speaking-live-cloud")
@SpringBootTest(properties={"server.address=127.0.0.1","server.port=0","damulab.speaking.enabled=true",
        "damulab.speaking.pilot-only=false","damulab.speaking.cloud.enabled=true","damulab.ai.real-providers-enabled=true",
        "damulab.speaking.budget.student-daily-usd=20.00","damulab.speaking.budget.module-daily-usd=50.00"},
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpeakingLiveEvaluationTest {
    private final AiProviderCode provider=AiProviderCode.valueOf(System.getenv().getOrDefault("SPEAKING_QA_PROVIDER","DEEPSEEK"));
    private final String model=System.getenv().getOrDefault("SPEAKING_QA_MODEL",provider==AiProviderCode.OPENAI?"gpt-4.1-mini":"deepseek-flash");
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final List<Map<String,Object>> results=new ArrayList<>();
    private final Map<String,JsonNode> diagnostics=new LinkedHashMap<>();
    @Autowired LiveSpeakingTutor tutor;
    @Autowired SpeakingScenarioCatalog catalog;
    @org.springframework.boot.test.mock.mockito.SpyBean SpeakingLlmClient client;
    @Autowired AiRuntimeSettingRepository routes;
    @Autowired SpeakingBudgetService budget;
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry){
        String url="jdbc:h2:mem:speaking-live-evaluation-"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.driver-class-name",()->"org.h2.Driver");
        registry.add("spring.datasource.username",()->"sa");registry.add("spring.datasource.password",()->"");
    }
    @BeforeAll void configure()throws Exception{
        assertThat(provider).isIn(AiProviderCode.OPENAI,AiProviderCode.DEEPSEEK);
        assertThat(System.getenv(provider==AiProviderCode.OPENAI?"OPENAI_API_KEY":"DEEPSEEK_API_KEY")).as("QA key must be explicitly provided in process environment").isNotBlank();
        assertThat(corpus()).hasSize(50);
        var route=routes.findById(AiUsageType.SPEAKING).orElseThrow();route.update(provider,model,"synthetic-live-qa");routes.saveAndFlush(route);
    }
    @BeforeEach void captureSyntheticOnly(){
        diagnostics.clear();org.mockito.Mockito.doAnswer(call->{
            JsonNode value=(JsonNode)call.callRealMethod();diagnostics.put(call.getArgument(3).toString(),value);return value;
        }).when(client).json(org.mockito.Mockito.any(),org.mockito.Mockito.anyString(),org.mockito.Mockito.any(),org.mockito.Mockito.any());
    }
    Stream<Arguments> cases()throws Exception{
        String only=System.getenv("SPEAKING_QA_CASE");
        return corpus().stream().filter(c->only==null||only.isBlank()||c.path("id").asText().equals(only)).map(c->Arguments.of(c.path("id").asText(),c));
    }
    private List<JsonNode> corpus()throws Exception{
        try(var input=getClass().getResourceAsStream("/speaking/evaluation-v2.json")){
            var root=json.readTree(input);var rows=new ArrayList<JsonNode>();root.forEach(rows::add);return rows;
        }
    }
    @ParameterizedTest(name="{0}") @MethodSource("cases")
    @WithMockUser(username="student@damulab.kz",roles="STUDENT")
    void evaluatesActualGuardTutorPipeline(String id,JsonNode c){
        var scenario=catalog.find(c.path("scenario").asText());String level=c.path("level").asText(),text=c.path("text").asText();
        long started=System.nanoTime();var report=new LinkedHashMap<String,Object>();
        report.put("id",id);report.put("scenario",scenario.code());report.put("level",level);report.put("expected",c.path("expected").asText());
        String actual;SpeakingTutor.Reply reply=null;
        try{reply=tutor.answer(new LiveSpeakingTutor.Context(scenario,level,c.path("language").asText(),
                scenario.firstQuestions().get(level),0,List.of(),text,List.of()),client.selection());actual=reply.disposition();}
        catch(org.springframework.web.server.ResponseStatusException ex){actual="HTTP_"+ex.getStatusCode().value();report.put("code",Objects.toString(ex.getReason(),"unavailable"));}
        catch(RuntimeException ex){actual="ERROR";}
        boolean passed=actual.equals(c.path("expected").asText());
        if(reply!=null){
            report.put("reply",reply);
            if(c.has("skill")){
                String target=c.path("correctedContains").asText();
                passed&=reply.feedback().stream().anyMatch(n->n.skillCode().equals(c.path("skill").asText())&&text.replace(n.original(),n.corrected()).contains(target));
            }else if(actual.equals("NORMAL"))passed&=reply.feedback().isEmpty();
        }
        report.put("diagnostics",new LinkedHashMap<>(diagnostics));
        report.put("actual",actual);report.put("passed",passed);report.put("millis",(System.nanoTime()-started)/1_000_000);results.add(report);
        assertThat(passed).as(id+" / "+actual).isTrue();
    }
    @AfterAll void saveReport()throws Exception{
        Path file=Path.of("build/qa/speaking/"+(provider==AiProviderCode.OPENAI?"live-evaluation-v2-openai.json":"live-evaluation-v2.json"));Files.createDirectories(file.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),Map.of("model",model,"provider",provider.name(),"promptVersion",LiveSpeakingTutor.REVISION,
                "syntheticOnly",true,"results",results,"budget",budget.report(),"checkedAt",java.time.Instant.now()));
    }
}

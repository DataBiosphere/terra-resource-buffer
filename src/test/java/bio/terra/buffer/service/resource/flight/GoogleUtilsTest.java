package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.BaseUnitTest;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleUtilsTest extends BaseUnitTest {

    @Test
    public void removeUserFromPolicyTest() {
        String testServiceAccount = "serviceAccount:test-service-account@test.com";
        List<String> rolesToRemove = List.of("roles/serviceusage.serviceUsageAdmin", "roles/resourcemanager.projectIamAdmin");
        Policy originalPolicy = new Policy()
            .setBindings(List.of(
                    createBindingWithUserAndRole("roles/editor", testServiceAccount),
                    createBindingWithUserAndRole("roles/serviceusage.serviceUsageAdmin", testServiceAccount),
                    createBindingWithUserAndRole("roles/resourcemanager.projectIamAdmin", testServiceAccount)));
        Policy newPolicy = GoogleUtils.removeUserRolesFromPolicy(originalPolicy, testServiceAccount, rolesToRemove);
        assertFalse(newPolicy.getBindings().stream()
            .anyMatch(binding -> binding.getMembers().contains(testServiceAccount)
                && rolesToRemove.contains(binding.getRole())));
    }

    @Test
    public void testIsNotAvailableToConsumerError() {
        String errorMessage = "Bind permission denied for service: genomics.googleapis.com\nService genomics.googleapis.com is not available to this consumer.\nHelp Token: ...";
        GoogleJsonError.ErrorInfo errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setDomain("global");
        errorInfo.setMessage(errorMessage);
        errorInfo.setReason("forbidden");

        GoogleJsonError error = new GoogleJsonError();
        error.setMessage(errorMessage);
        error.setErrors(List.of(errorInfo));

        GoogleJsonResponseException ex = mock(GoogleJsonResponseException.class);
        when(ex.getDetails()).thenReturn(error);
        assertTrue(GoogleUtils.isNotAvailableToConsumer(ex));
    }

    @Test
    public void testIsAvailableToConsumerError() {
        String errorMessage = "Some other error message unrelated to service availability";
        GoogleJsonError.ErrorInfo errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setDomain("global");
        errorInfo.setMessage(errorMessage);
        errorInfo.setReason("forbidden");

        GoogleJsonError error = new GoogleJsonError();
        error.setMessage(errorMessage);
        error.setErrors(List.of(errorInfo));

        GoogleJsonResponseException ex = mock(GoogleJsonResponseException.class);
        when(ex.getDetails()).thenReturn(error);
        assertFalse(GoogleUtils.isNotAvailableToConsumer(ex));
    }

    private Binding createBindingWithUserAndRole(String user, String role) {
        return new Binding().setRole(role).setMembers(List.of(user));
    }

}
package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.BaseUnitTest;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private Binding createBindingWithUserAndRole(String user, String role) {
        return new Binding().setRole(role).setMembers(List.of(user));
    }

}
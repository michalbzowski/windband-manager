package pl.michalbzowski.windband.application.dto;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API contract regression test for {@link InviteOptionsDto}.
 *
 * <p>This test pins the public shape of the DTO: every component name and its
 * type in declaration order. A future breaking rename, removal, re-typing, or
 * reordering of any public component will fail CI immediately.
 */
class InviteOptionsDtoTest {

    @Nested
    class TopLevel {

        @Test
        void shouldHaveExpectedComponents() {
            List<String> expected = List.of(
                    "groups",
                    "members"
            );
            assertThat(components(InviteOptionsDto.class)).isEqualTo(expected);
        }

        @Test
        void shouldHaveExpectedTypes() {
            assertType("groups", "java.util.List<pl.michalbzowski.windband.application.dto.InviteOptionsDto$GroupOption>");
            assertType("members", "java.util.List<pl.michalbzowski.windband.application.dto.InviteOptionsDto$MemberOption>");
        }
    }

    @Nested
    class GroupOption {

        @Test
        void shouldHaveExpectedComponents() {
            List<String> expected = List.of(
                    "id",
                    "name",
                    "memberCount",
                    "memberIds"
            );
            assertThat(components(InviteOptionsDto.GroupOption.class)).isEqualTo(expected);
        }

        @Test
        void shouldHaveExpectedTypes() {
            assertType("id", "java.lang.Long");
            assertType("name", "java.lang.String");
            assertType("memberCount", "int");
            assertType("memberIds", "java.util.List<java.lang.Long>");
        }
    }

    @Nested
    class MemberOption {

        @Test
        void shouldHaveExpectedComponents() {
            List<String> expected = List.of(
                    "id",
                    "name"
            );
            assertThat(components(InviteOptionsDto.MemberOption.class)).isEqualTo(expected);
        }

        @Test
        void shouldHaveExpectedTypes() {
            assertType("id", "java.lang.Long");
            assertType("name", "java.lang.String");
        }
    }

    private static List<String> components(Class<?> recordClass) {
        List<String> result = new ArrayList<>();
        for (RecordComponent component : recordClass.getRecordComponents()) {
            result.add(component.getName());
        }
        return result;
    }

    private static void assertType(String name, String expectedTypeName) {
        RecordComponent comp = findComponent(InviteOptionsDto.class, name);
        if (comp == null) {
            comp = findComponent(InviteOptionsDto.GroupOption.class, name);
        }
        if (comp == null) {
            comp = findComponent(InviteOptionsDto.MemberOption.class, name);
        }
        assertThat(comp).as("component %s", name).isNotNull();
        assertThat(comp.getGenericType().getTypeName()).as("type of %s", name).isEqualTo(expectedTypeName);
    }

    private static RecordComponent findComponent(Class<?> clazz, String name) {
        for (RecordComponent component : clazz.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        return null;
    }
}

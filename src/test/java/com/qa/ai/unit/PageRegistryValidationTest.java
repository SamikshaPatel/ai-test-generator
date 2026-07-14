package com.qa.ai.unit;

import com.qa.ai.pages.BasePage;
import com.qa.ai.pages.PageRegistry;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates that every non-abstract BasePage subclass in com.qa.ai.pages
 * is registered in PageRegistry.PAGES.
 *
 * WHY THIS EXISTS:
 *   PageRegistry.PAGES is a hardcoded list. If a developer adds a new page
 *   class (e.g. CheckoutPage) but forgets to add it to the list, self-healing
 *   silently stops working for that page — no error, just missing fallbacks.
 *   This test catches that at build time.
 *
 * HOW IT WORKS:
 *   Scans the com.qa.ai.pages package via the classloader, finds all concrete
 *   BasePage subclasses, instantiates each one, and checks that its selectors
 *   appear in PageRegistry.allSelectors(). If a page is not in PAGES, none
 *   of its selector keys will be present in the merged registry map.
 */
public class PageRegistryValidationTest {

    @Test(description = "All BasePage subclasses in com.qa.ai.pages are registered in PageRegistry")
    public void allPageObjectsRegisteredInPageRegistry() throws Exception {
        List<Class<? extends BasePage>> found = findConcretePageSubclasses();
        Assert.assertFalse(found.isEmpty(),
                "No BasePage subclasses found in com.qa.ai.pages — check package scan");

        Map<String, String> registered = PageRegistry.allSelectors();
        List<String> unregistered = new ArrayList<>();

        for (Class<? extends BasePage> clazz : found) {
            BasePage instance = clazz.getDeclaredConstructor().newInstance();
            Map<String, String> pageSelectors = instance.selectors();

            if (pageSelectors.isEmpty()) continue; // page with no selectors is valid

            boolean anyKeyPresent = pageSelectors.keySet().stream()
                    .anyMatch(registered::containsKey);

            if (!anyKeyPresent) {
                unregistered.add(clazz.getSimpleName());
            }
        }

        Assert.assertTrue(unregistered.isEmpty(),
                "The following BasePage subclasses are not registered in PageRegistry.PAGES " +
                "— add 'new " + String.join("(), new ", unregistered) + "()' to the PAGES list: "
                + unregistered);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Scans com.qa.ai.pages for all compiled .class files that are:
     *   - a subclass of BasePage
     *   - not BasePage itself
     *   - not abstract
     */
    private List<Class<? extends BasePage>> findConcretePageSubclasses() throws Exception {
        String packageName = "com.qa.ai.pages";
        String packagePath = packageName.replace('.', '/');

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(packagePath);
        Assert.assertNotNull(resource,
                "Package not found on classpath: " + packageName);

        File directory = new File(resource.toURI());
        List<Class<? extends BasePage>> result = new ArrayList<>();

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (!file.getName().endsWith(".class")) continue;

            String className = packageName + "." + file.getName().replace(".class", "");
            Class<?> clazz = Class.forName(className);

            if (BasePage.class.isAssignableFrom(clazz)
                    && !clazz.equals(BasePage.class)
                    && !Modifier.isAbstract(clazz.getModifiers())) {
                result.add(clazz.asSubclass(BasePage.class));
            }
        }
        return result;
    }
}

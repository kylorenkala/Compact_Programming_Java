import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

// Import JUnit 5 assertions
import static org.junit.jupiter.api.Assertions.*;


class PartDefinitionsTest {

    @Test
    void testCreateSampleParts() {
        // Act: Call the static method
        List<Part> sampleParts = PartDefinitions.createSampleParts();

        // Assert:
        // 1. The list should exist
        assertNotNull(sampleParts, "The returned list of parts should not be null");

        // 2. The list should not be empty
        assertFalse(sampleParts.isEmpty(), "The list of parts should not be empty");

        // 3. Check for a reasonable number of parts (20 in the source)
        assertEquals(20, sampleParts.size(), "Should be 20 sample parts defined");

        // 4. Spot check the first part
        Part firstPart = sampleParts.get(0);
        assertEquals("P1001", firstPart.partID(), "First part ID is incorrect");
        assertEquals("Oil Filter", firstPart.name(), "First part name is incorrect");
    }


    @Test
    void testGetInitialStock() {
        // Arrange: Get the list of parts first, as the method depends on it.
        List<Part> sampleParts = PartDefinitions.createSampleParts();

        // Act: Call the static method with the part list
        Map<Part, Integer> initialStock = PartDefinitions.getInitialStock(sampleParts);

        // Assert:
        // 1. The map should exist
        assertNotNull(initialStock, "The returned stock map should not be null");

        // 2. The map should not be empty
        assertFalse(initialStock.isEmpty(), "The stock map should not be empty");

        // 3. Check for the number of stocked parts (10 in the source)
        assertEquals(10, initialStock.size(), "Should be 10 parts with initial stock");

        // 4. Spot check the stock for the first part (P1001)
        Part firstPart = sampleParts.get(0);
        assertTrue(initialStock.containsKey(firstPart), "Stock map should contain the first part");
        assertEquals(25, initialStock.get(firstPart), "Stock for P1001 (Oil Filter) should be 25");

        // 5. Spot check the stock for the second part (P1002)
        Part secondPart = sampleParts.get(1);
        assertTrue(initialStock.containsKey(secondPart), "Stock map should contain the second part");
        assertEquals(30, initialStock.get(secondPart), "Stock for P1002 (Air Filter) should be 30");
    }
}
package com.yishenghuang.heartext

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupPolicyTest {
    @Test fun installedApplicationDisablesBackupAndPackagedRulesExcludePrivateData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val domains = setOf("root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref")
        for ((resource, sections) in listOf(
            R.xml.backup_rules to setOf("full-backup-content"),
            R.xml.data_extraction_rules to setOf("cloud-backup", "device-transfer")
        )) {
            val found = sections.associateWith { mutableSetOf<String>() }
            context.resources.getXml(resource).use { xml ->
                var section: String? = null
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        if (xml.name in sections) section = xml.name
                        assertNotEquals("include", xml.name)
                        if (xml.name == "exclude") {
                            assertEquals(".", xml.getAttributeValue(null, "path"))
                            found.getValue(requireNotNull(section)).add(xml.getAttributeValue(null, "domain"))
                        }
                    }
                    if (xml.eventType == XmlPullParser.END_TAG && xml.name == section) section = null
                    xml.next()
                }
            }
            found.forEach { (section, actual) -> assertEquals(section, domains, actual) }
        }
    }
}

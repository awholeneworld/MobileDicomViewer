package gachon.dicomviewer;

import java.io.File;

public class DICOMFileFilter implements java.io.FileFilter {

    public boolean accept(File pathname) {

        if (pathname.isFile() && !pathname.isHidden()) {

            // Get the file name
            String fileName = pathname.getName();

            // If the file is a DICOMDIR return false
            if (fileName.equals("DICOMDIR"))
                return false;

            // Get the dot index
            int dotIndex = fileName.lastIndexOf(".");

            // If the dotIndex is equal to -1 this is
            // a file without extension so consider it as DICOM file
            if (dotIndex == -1)
                return true;

            // Check the file extension
            String fileExtension = fileName.substring(dotIndex + 1);

            if (fileExtension.equalsIgnoreCase("dcm"))
                return true;

        }

        return false;
    }
}

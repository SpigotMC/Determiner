package net.md_5.determiner;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Mojo which replaces usages of non deterministic classes with deterministic
 * equivalents.
 */
@Mojo(name = "transform", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class TransformMojo extends AbstractMojo
{

    /**
     * Directory of classes to process.
     */
    @Parameter(property = "project.build.outputDirectory", required = true)
    private File classDirectory;
    /**
     * Counter of how many classes we have processed.
     */
    private int classCount;
    /**
     * Counter of how many classes we have skipped.
     */
    private int skipCount;
    /**
     * Counter of how many things we have transformed.
     */
    private int transformCount;
    /**
     * Nondeterministic classes and their replacements.
     */
    @Parameter
    private Map<String, String> replacements;

    @Override
    public void execute() throws MojoExecutionException
    {
        if ( replacements == null )
        {
            replacements = new HashMap<>();
            replacements.put( "java/util/HashMap", "java/util/LinkedHashMap" );
            replacements.put( "java/util/HashSet", "java/util/LinkedHashSet" );

            getLog().info( "Using default replacements" );
        }

        walk( classDirectory, new SuffixFilter( ".class" ) );

        getLog().info( "Read " + classCount + " class" + plural( classCount, "es" ) + ", skipped " + skipCount
                + ", transformed " + transformCount + " instance" + plural( transformCount, "s" ) + " of nondeterninisms." );
    }

    private String plural(int count, String plural)
    {
        return ( count != 1 ) ? plural : "";
    }

    private void walk(File dir, FileFilter filter) throws MojoExecutionException
    {
        for ( File file : dir.listFiles( filter ) )
        {
            if ( file.isDirectory() )
            {
                walk( file, filter );
            } else
            {
                process( file );
            }
        }
    }

    private void process(File file) throws MojoExecutionException
    {
        try
        {
            ClassReader reader;
            try ( FileInputStream is = new FileInputStream( file ) )
            {
                reader = new ClassReader( is );
            }

            ClassWriter writer = new ClassWriter( reader, 0 );
            ClassTransformer transformer = new ClassTransformer( writer );
            reader.accept( transformer, 0 );

            // Already transformed at another point?
            if ( !transformer.transformed )
            {
                // Add transformed marker
                writer.visitAttribute( new Determined() );

                try ( FileOutputStream out = new FileOutputStream( file ) )
                {
                    out.write( writer.toByteArray() );
                }
            }
        } catch ( IOException ex )
        {
            throw new MojoExecutionException( "Error whilst reading / writing class file : " + file, ex );
        }
    }

    private class ClassTransformer extends ClassVisitor
    {

        private boolean transformed;

        public ClassTransformer(ClassVisitor cv)
        {
            super( Opcodes.ASM9, cv );
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
        {
            classCount++;

            String replacementType = replacements.get( superName );
            if ( replacementType != null )
            {
                superName = replacementType;

                transformCount++;
            }

            super.visit( version, access, name, signature, superName, interfaces );
        }

        @Override
        public void visitAttribute(Attribute attr)
        {
            if ( attr.type.equals( Determined.TYPE ) )
            {
                if ( transformed )
                {
                    throw new IllegalStateException( "Duplicate Determiner attribute!" );
                }

                skipCount++;
                transformed = true;
            }

            super.visitAttribute( attr );
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
        {
            if ( transformed )
            {
                return super.visitMethod( access, name, desc, signature, exceptions );
            }

            return new MethodTransformer( super.visitMethod( access, name, desc, signature, exceptions ) );
        }

        private class MethodTransformer extends MethodVisitor
        {

            public MethodTransformer(MethodVisitor mv)
            {
                super( Opcodes.ASM9, mv );
            }

            @Override
            public void visitTypeInsn(int opcode, String type)
            {
                if ( opcode == Opcodes.NEW )
                {
                    String replacementType = replacements.get( type );
                    if ( replacementType != null )
                    {
                        type = replacementType;

                        // Only track NEW replacements
                        transformCount++;
                    }
                }

                super.visitTypeInsn( opcode, type );
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf)
            {
                if ( name.equals( "<init>" ) )
                {
                    String replacementType = replacements.get( owner );
                    if ( replacementType != null )
                    {
                        owner = replacementType;
                    }
                }

                super.visitMethodInsn( opcode, owner, name, desc, itf );
            }
        }
    }

    private static class Determined extends Attribute
    {

        public static final String TYPE = "Determined";

        public Determined()
        {
            super( TYPE );
        }

        @Override
        protected ByteVector write(ClassWriter cw, byte[] code, int len, int maxStack, int maxLocals)
        {
            return new ByteVector( 0 );
        }
    }

    private static class SuffixFilter implements FileFilter
    {

        private final String suffix;

        public SuffixFilter(String suffix)
        {
            this.suffix = suffix;
        }

        @Override
        public boolean accept(File pathname)
        {
            return pathname.isDirectory() || pathname.getName().endsWith( suffix );
        }
    }
}
